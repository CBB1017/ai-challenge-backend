package com.brycenkorea.template.config;

import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.util.ChatStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

    public IntentRouter(ChatClient.Builder builder, ChatStateManager stateManager) {
        // 라우터 전용으로 가볍고 빠른 모델(예: Gemini 1.5 Flash)을 세팅하면 비용과 속도를 아낄 수 있습니다.
        this.routerClient = builder.defaultSystem(
            "당신은 사내 그룹웨어 사용자의 요청 의도를 분류하는 라우터입니다. " +
                "사용자의 현재 입력과, 필요한 경우 직전 대화 맥락을 분석하여 다음 중 하나로 분류하세요: " +
                "OVERTIME(잔업/특근 신청), VACATION(휴가/연차 신청), POLICY(사내 규정 문의), GENERAL(일반 대화). " +
                "만약 사용자가 '응', '진행해', '맞아' 등 긍정/동의의 대답을 했다면, 직전 AI 질문의 문맥을 따라가세요."
        ).build();
        this.stateManager = stateManager;

        // TODO: 실제 환경에서는 DB나 YAML에서 읽어와 Registry를 초기화합니다.
        initSOPRegistry();
    }

    /**
     * 🚀 [변경됨] Mono를 반환하는 비동기 라우터로 진화
     */
    public Mono<AgentWorkflowSOP> determineSop(String userMessage, String roomId) {
        // 1. Redis에서 현재 이 방이 '결재 대기 중' 상태인지 확인
        return stateManager.getState(roomId)
            .flatMap(state -> {
                log.info("[Redis 검문소 통과] 진행 중인 SOP: {}", state);

                // 결재 대기 중인데 사용자가 "취소", "아니" 라고 한 경우
                if (isCancelIntent(userMessage)) {
                    log.info("사용자 취소 요청 - 상태 초기화");
                    return stateManager.clearState(roomId)
                        .then(Mono.just(sopRegistry.get("GENERAL")));
                }

                // 진행 대기 상태에서 동의("응", "상신해")를 한 경우
                if ("OVERTIME_WAITING".equals(state)) {
                    if (isConfirmIntent(userMessage)) {
                        log.info("사용자 승인 확인 - 결재 상신 플로우로 넘기고 상태는 초기화(Clear) 합니다.");
                        // 여기서 상태를 지워주어야 상신 후 다음 질문("휴가도 신청해줘" 등)이 먹힙니다.
                        return stateManager.clearState(roomId)
                            .thenReturn(sopRegistry.get("OVERTIME"));
                    }
                    // 승인도 거절도 아닌 다른 대답("내일로 바꿔줘" 등)이면 계속 가둬둡니다.
                    return Mono.just(sopRegistry.get("OVERTIME"));
                }

                // 알 수 없는 상태면 GENERAL
                return Mono.just(sopRegistry.get("GENERAL"));
            })
            // 2. Redis에 상태가 없으면? (평소 대화) -> 기존 로직(fastMatch & LLM) 가동
            .switchIfEmpty(Mono.defer(() -> {
                log.info("상태 없음, 일반 라우터 가동");

                AgentWorkflowSOP matchedSop = fastMatch(userMessage);
                log.info("기존 로직의 결과 => {}", matchedSop);
                if (matchedSop != null) {
                    return applyStateAndReturn(roomId, matchedSop);
                }

                return Mono.fromCallable(() -> classifyLlm(userMessage))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(sop -> applyStateAndReturn(roomId, sop));
            }));
    }

    // 🚀 [새로 추가된 로직] 특정 SOP로 분류되었을 때 상태를 잠그는(Set) 역할
    private Mono<AgentWorkflowSOP> applyStateAndReturn(String roomId, AgentWorkflowSOP sop) {
        // OVERTIME 플로우에 처음 진입했다면 상태를 저장합니다.
        if ("OVERTIME".equals(sop.intentId())) {
            log.info("🔒 [상태 잠금] {} 방에 OVERTIME_WAITING 상태 부여", roomId);
            return stateManager.setState(roomId, "OVERTIME_WAITING")
                .thenReturn(sop);
        }
        return Mono.just(sop);
    }

    // 기존 취소 의도 파악
    private boolean isCancelIntent(String text) {
        String clean = text.replaceAll("\\s+", "");
        return clean.contains("아니") || clean.contains("취소") || clean.contains("됐어") || clean.contains("하지마");
    }

    // 🚀 [새로 추가된 로직] 승인 의도 파악
    private boolean isConfirmIntent(String text) {
        String clean = text.replaceAll("\\s+", "");
        return clean.contains("응") || clean.contains("어") || clean.contains("맞아") ||
            clean.contains("진행해") || clean.contains("상신해") || clean.contains("해줘");
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
        // 공백 제거 및 소문자 변환으로 검색 확률을 높임
        String cleanText = text.replaceAll("\\s+", "").toLowerCase();

        if (cleanText.contains("ot") || cleanText.contains("잔업") || cleanText.contains("특근") || cleanText.contains("야근") || cleanText.contains(
            "초과근무"))
        {
            return sopRegistry.get("OVERTIME");
        }
        if (cleanText.contains("휴가") || cleanText.contains("연차") || cleanText.contains("반차") || cleanText.contains("반반차") || cleanText.contains(
            "보상휴가") || cleanText.contains("결근"))
        {
            return sopRegistry.get("VACATION");
        }
        if (cleanText.contains("규정") || cleanText.contains("사규") || cleanText.contains("지침") || cleanText.contains("매뉴얼")) {
            return sopRegistry.get("POLICY");
        }

        return null;
    }

    // 메인 분류 메서드
    public AgentWorkflowSOP classify(String userMessage) {

        // 1단계: 고속 라우터가 먼저 처리
        AgentWorkflowSOP matchedSop = fastMatch(userMessage);

        if (matchedSop != null) {
            log.info("⚡ Fast Router 매칭 성공: {}", matchedSop.intentId());
            return matchedSop;
        }

        // 2단계: 키워드에 안 걸린 복잡한 문장만 LLM 라우터 가동
        log.info("🤖 LLM Router 가동 (의도 파악 중)...");
        try {
            IntentClassification result = routerClient.prompt()
                .user(userMessage)
                .call()
                .entity(IntentClassification.class);

            return sopRegistry.getOrDefault(Objects.requireNonNull(result).category(), sopRegistry.get("GENERAL"));
        } catch (Exception e) {
            log.info("LLM 라우터 실패, 기본값 반환: {}", e.getMessage());
            return sopRegistry.get("GENERAL");
        }
    }

    private void initSOPRegistry() {
        sopRegistry.put(
            "OVERTIME", new AgentWorkflowSOP(
                "OVERTIME", """
                     당신은 현재 [잔업/특근 신청 워크플로우]를 수행 중입니다.
                     아래의 절차(<step>)와 제약사항(<constraint>)을 엄격하게 준수하세요.
                    \s
                     <step>
                     1. 사용자가 시간을 명시하지 않았다면, '근태_조회_MCP'를 호출하여 오늘 또는 해당 날짜의 실제 퇴근 시간을 조회한다.
                     2. 조회된 퇴근 시간을 분석하여 규정에 맞는 잔업 인정 시간(예: 19:00 ~ 21:30)을 계산한다.
                     3. 계산된 시간을 사용자에게 친절하게 안내하고, "이 시간으로 결재 상신을 진행할까요?"라고 묻는다.
                     4. 사용자가 동의(승인)하면, 비로소 '잔업_상신_MCP'를 호출하여 기안을 완료한다.
                     </step>
                    \s
                     <constraint>
                     - 오늘 또는 해당 날짜의 실제 근무 시간이 없더라도 안내 후 사용자가 재차 요청을 하면 STEP 1,2를 생략한다
                     - STEP 3을 수행한 직후에는 반드시 [STOP] 하고 사용자의 대답을 기다려야 합니다.
                     - 사용자의 명시적인 '승인' 응답이 존재하기 전까지는 절대로 STEP 4(잔업_상신_MCP)를 선제적으로 호출하지 마십시오.
                     </constraint>
                """, false
            )
        );
        sopRegistry.put(
            "POLICY",
            new AgentWorkflowSOP("POLICY", "[사내 규정 답변]\n검색된 RAG 문서를 바탕으로 친절하고 정확하게 답변하세요.", true)
        );
        sopRegistry.put("GENERAL", new AgentWorkflowSOP("GENERAL", "특별한 도구 호출 없이 친절하게 응대하세요.", false));
    }

    // Spring AI가 구조화된 출력을 위해 사용할 내부 레코드
    record IntentClassification(String category, double confidence) {}
}