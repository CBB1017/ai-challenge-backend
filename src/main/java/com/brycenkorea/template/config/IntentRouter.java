package com.brycenkorea.template.config;

import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.util.ChatStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class IntentRouter {

    private final ChatClient routerClient;
    private final ChatStateManager stateManager;
    private final Map<String, AgentWorkflowSOP> sopRegistry = new ConcurrentHashMap<>();

    public IntentRouter(ChatClient.Builder builder, ChatMemory chatMemory, ChatStateManager stateManager) {
        // 라우터 전용으로 가볍고 빠른 모델(예: Gemini 1.5 Flash)을 세팅하면 비용과 속도를 아낄 수 있습니다.
        this.routerClient = builder.defaultSystem(
            "당신은 사내 그룹웨어 사용자의 요청 의도를 분류하는 라우터입니다. " +
                "사용자의 현재 입력과, 필요한 경우 직전 대화 맥락을 분석하여 다음 중 하나로 분류하세요: " +
                "OVERTIME_ONEDAY(특정 날짜의 잔업/특근 신청), OVERTIME_MONTHLY(특정 달의 잔업/특근 신청), VACATION(휴가/연차 신청), POLICY(사내 규정 문의), GENERAL(일반 대화). " +
                "만약 사용자가 '응', '진행해', '맞아' 등 긍정/동의의 대답을 했다면, 직전 AI 질문의 문맥을 따라가세요."
        ).build();
        this.stateManager = stateManager;

        // TODO: 실제 환경에서는 DB나 YAML에서 읽어와 Registry를 초기화합니다.
        initSOPRegistry();
    }

    /**
     * 🚀 [고도화됨] LLM Fallback 및 주제 전환 안내가 포함된 비동기 라우터
     */
    public Mono<AgentWorkflowSOP> determineSop(String userMessage, String roomId) {
        return stateManager.getState(roomId)
            .flatMap(state -> {
                // Redis 에러 발생 시 안내 문구 주입 (5번)
                if ("REDIS_ERROR".equals(state)) {
                    log.warn("Redis 에러 감지 - 사용자에게 안내 문구 주입");
                    AgentWorkflowSOP general = sopRegistry.get("GENERAL");
                    String warning = "\n\n[주의] 현재 시스템 문제로 대화 맥락 유지(상태 관리)가 원활하지 않을 수 있습니다. 중요한 상신 전에는 다시 한번 확인해 주세요.";
                    return Mono.just(new AgentWorkflowSOP(general.intentId(), general.rules() + warning, general.requiresRag()));
                }

                log.info("[Redis 검문소 통과] 진행 중인 상태: {}", state);

                // 1. 상태가 있더라도 사용자가 아예 다른 명확한 요청을 했는지 확인 (Topic Switching)
                AgentWorkflowSOP fastIntent = fastMatch(userMessage);
                if (fastIntent != null) {
                    String expectedState = fastIntent.intentId() + "_WAITING";
                    if (!state.startsWith(expectedState) && !"OVERTIME_MONTHLY".equals(fastIntent.intentId())) {
                        log.info("사용자 의도 변경 감지 - 기존 상태({}) 초기화 후 {} 플로우 진입", state, fastIntent.intentId());
                        return stateManager.clearState(roomId)
                            .then(applyStateAndReturn(roomId, fastIntent))
                            .map(sop -> injectSwitchNotice(sop, state)); // 주제 전환 알림 주입
                    }
                }

                // 2. 취소 의도 파악 (확장된 단어셋)
                if (isCancelIntent(userMessage)) {
                    log.info("사용자 취소 요청 - 상태 초기화");
                    return stateManager.clearState(roomId)
                        .thenReturn(sopRegistry.get("GENERAL"));
                }

                // 3. 현재 진행 중인 다중 턴 상태 처리 (Generic WAITING 처리)
                if (state.contains("_WAITING")) {
                    String baseIntentId = state.split(":")[0].replace("_WAITING", "");

                    if (isConfirmIntent(userMessage)) {
                        log.info("사용자 승인 확인 - {} 결재 상신 플로우로 넘기고 상태 초기화", baseIntentId);
                        return stateManager.clearState(roomId)
                            .thenReturn(sopRegistry.getOrDefault(baseIntentId, sopRegistry.get("GENERAL")));
                    }
                    return Mono.just(sopRegistry.getOrDefault(baseIntentId, sopRegistry.get("GENERAL")));
                }

                return Mono.just(sopRegistry.get("GENERAL"));
            })
            // 2. Redis에 상태가 없거나 에러 발생 시
            .switchIfEmpty(Mono.defer(() -> {
                log.info("상태 없음, 일반 라우터 가동");

                AgentWorkflowSOP matchedSop = fastMatch(userMessage);
                if (matchedSop != null) {
                    return applyStateAndReturn(roomId, matchedSop);
                } else {
                    // 1번 보안: 키워드 매칭 실패 시 비동기 LLM 라우팅 시도
                    return classifyLlmAsync(userMessage)
                        .flatMap(sop -> applyStateAndReturn(roomId, sop));
                }
            }));
    }

    // 특정 SOP로 분류되었을 때 상태를 잠그는(Set) 역할
    private Mono<AgentWorkflowSOP> applyStateAndReturn(String roomId, AgentWorkflowSOP sop) {
        // 단일 날짜 기반 다중 턴 워크플로우(OT 단일, 휴가)에 대해 상태를 저장합니다.
        if ("OVERTIME_ONEDAY".equals(sop.intentId()) || "VACATION".equals(sop.intentId())) {
            String stateName = sop.intentId() + "_WAITING";
            log.info("🔒 [상태 잠금] {} 방에 {} 상태 부여", roomId, stateName);
            return stateManager.setState(roomId, stateName)
                .thenReturn(sop);
        }
        return Mono.just(sop);
    }

    // 주제 전환 시 AI에게 사용자 알림을 유도하는 프롬프트 주입 (4번)
    private AgentWorkflowSOP injectSwitchNotice(AgentWorkflowSOP sop, String oldState) {
        String baseState = oldState.split(":")[0].replace("_WAITING", "");
        String notice = String.format("""
            [알림] 사용자가 기존 '%s' 관련 작업을 중단하고 새로운 요청을 했습니다. \
            기존 작업이 중단되었음을 언급하며 새로운 요청을 친절히 도와주세요.""", baseState);
        return new AgentWorkflowSOP(sop.intentId(), sop.rules() + notice, sop.requiresRag());
    }

    // LLM 분류 비동기 처리 (1번)
    private Mono<AgentWorkflowSOP> classifyLlmAsync(String userMessage) {
        return Mono.fromCallable(() -> classifyLlm(userMessage))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(java.time.Duration.ofSeconds(2)) // 라우팅 지연 방지
            .onErrorReturn(sopRegistry.get("GENERAL"));
    }

    // 2번: 확장된 취소 의도 파악
    private boolean isCancelIntent(String text) {
        String clean = text.replaceAll("\\s+", "");
        return clean.contains("아니") || clean.contains("취소") || clean.contains("됐어") ||
            clean.contains("하지마") || clean.contains("관둘래") || clean.contains("안할래") ||
            clean.contains("정지") || clean.contains("멈춰");
    }

    // 2번: 확장된 승인 의도 파악
    private boolean isConfirmIntent(String text) {
        String clean = text.replaceAll("\\s+", "");
        return clean.contains("응") || clean.contains("어") || clean.contains("맞아") ||
            clean.contains("진행해") || clean.contains("상신해") || clean.contains("해줘") ||
            clean.contains("그래") || clean.contains("좋아") || clean.contains("오케이") ||
            clean.contains("ok") || clean.contains("확인");
    }

    // 기존 LLM 분류 로직 분리
    private AgentWorkflowSOP classifyLlm(String userMessage) {
        try {
            IntentClassification result = routerClient.prompt()
                .user(userMessage)
                .call()
                .entity(IntentClassification.class);
            return sopRegistry.getOrDefault(Objects.requireNonNull(result).category(), sopRegistry.get("GENERAL"));
        } catch (Exception e) {
            return sopRegistry.get("GENERAL");
        }
    }

    // 1차 고속 라우터: 키워드 기반 분류
    private AgentWorkflowSOP fastMatch(String text) {
        String cleanText = text.replaceAll("\\s+", "").toLowerCase();

        if (cleanText.contains("ot") || cleanText.contains("잔업") || cleanText.contains("특근") ||
            cleanText.contains("야근") || cleanText.contains("초과근무")) {

            // 월 단위 요청인지 파악
            if (cleanText.contains("월") || cleanText.contains("이번달") || cleanText.contains("이전달") || cleanText.contains("저번달") || cleanText.contains("지난달")) {
                return sopRegistry.get("OVERTIME_MONTHLY");
            }
            // 그 외에는 전부 단일 날짜로 취급
            return sopRegistry.get("OVERTIME_ONEDAY");
        }

        if (cleanText.contains("휴가") || cleanText.contains("연차") || cleanText.contains("반차") ||
            cleanText.contains("반반차") || cleanText.contains("보상휴가") || cleanText.contains("결근")) {
            return sopRegistry.get("VACATION");
        }

        if (cleanText.contains("규정") || cleanText.contains("사규") || cleanText.contains("지침") || cleanText.contains("매뉴얼")) {
            return sopRegistry.get("POLICY");
        }

        return null;
    }

    private void initSOPRegistry() {
        // 1. 단일 날짜 전용 플로우 (기존과 동일 + 명확화)
        sopRegistry.put(
            "OVERTIME_ONEDAY", new AgentWorkflowSOP(
                "OVERTIME_ONEDAY", """
                     당신은 현재 [단일 날짜 잔업/특근 신청 워크플로우]를 수행 중입니다.
                     아래의 절차(<step>)와 제약사항(<constraint>)을 엄격하게 준수하세요.
                
                     <step>
                     1. 사용자가 시간을 명시하지 않았다면 다시 사용자에게 정확한 ot_date를 물어보게 한다.
                     2. 'get_team_attendance'를 호출하여 해당 날짜의 실제 퇴근 시간을 조회한다.
                     3. 계산된 시간을 사용자에게 친절하게 안내하고, "이 시간으로 결재 상신을 진행할까요?"라고 묻는다.
                     4. 사용자가 동의(승인)하면, 'request_overtime_approval'(request_type="oneday")를 호출하여 기안을 완료한다.
                     </step>
                
                     <constraint>
                     - STEP 3을 수행한 직후에는 반드시 [STOP] 하고 사용자의 대답을 기다려야 합니다.
                     - 사용자의 명시적인 '승인' 응답이 존재하기 전까지는 절대로 STEP 4를 선제적으로 호출하지 마십시오.
                     </constraint>
                """, false
            )
        );

        // 2. 월 단위 전용 플로우 (원샷 실행)
        sopRegistry.put(
            "OVERTIME_MONTHLY", new AgentWorkflowSOP(
                "OVERTIME_MONTHLY", """
                     당신은 현재 [월 단위 잔업/특근 일괄 신청 워크플로우]를 수행 중입니다.
                     월 단위 상신은 도구 내부에서 일괄 처리되므로 사전 승인 절차가 필요 없습니다.
                
                     <step>
                     1. 대화 문맥에서 사용자가 원하는 연도(target_year)와 월(target_month)을 지정했다면 값을 세팅하고, 이번달 또는 저번달과 같은 표현했다면 문자열 그대로 보냅니다.
                     2. 사용자에게 되묻지 말고 즉시 '잔업_상신_MCP'(request_type="monthly") 도구를 호출하세요.
                     3. 상신 도구의 실행 결과(성공/실패 메시지)를 바탕으로 사용자에게 친절하게 최종 처리 내역을 안내하고 대화를 종료합니다.
                     </step>
                
                     <constraint>
                     - 중간 확인 과정 없이 즉시 MCP 도구를 실행해야 합니다.
                     </constraint>
                """, false
            )
        );

        sopRegistry.put(
            "VACATION", new AgentWorkflowSOP(
                "VACATION", """
                     당신은 현재 [휴가/연차 신청 워크플로우]를 수행 중입니다.
                     아래의 절차(<step>)와 제약사항(<constraint>)을 엄격하게 준수하세요.
                
                     <step>
                     1. 사용자가 원하는 휴가 날짜(시작일과 종료일)와 휴가 유형(연차, 반차 등)을 파악합니다. 누락되었다면 질문하세요.
                     2. 파악된 정보를 바탕으로 사용자에게 "이 내용으로 휴가를 신청할까요?"라고 묻습니다.
                     3. 사용자가 동의(승인)하면, 'request_vacation_approval' 도구를 호출하여 기안을 완료합니다.
                     </step>
                
                     <constraint>
                     - STEP 2를 수행한 직후에는 반드시 [STOP] 하고 사용자의 대답을 기다려야 합니다.
                     - 사용자의 명시적인 '승인' 응답이 존재하기 전까지는 절대로 STEP 3을 선제적으로 호출하지 마십시오.
                     </constraint>
                """, false
            )
        );

        sopRegistry.put("POLICY", new AgentWorkflowSOP("POLICY", "[사내 규정 답변]\n검색된 RAG 문서를 바탕으로 친절하고 정확하게 답변하세요.", true));
        sopRegistry.put("GENERAL", new AgentWorkflowSOP("GENERAL", """
            일반 대화로 분기되었지만 MCP 도구를 호출해야 할수도 있습니다.
            이전 대화를 참조하고 적절하고 빠른 판단으로 사용자의 요청을 해결하세요.
            판단이 어려우면 바로 사용자에게 다시 질문하세요.
            """, false));
    }

    // Spring AI가 구조화된 출력을 위해 사용할 내부 레코드
    record IntentClassification(String category, double confidence) {}
}