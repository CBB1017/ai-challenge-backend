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
        // 라우터 전용으로 가볍고 빠른 모델을 세팅합니다.
        this.routerClient = builder.defaultSystem(
            """
                You are a high-performance router that classifies the intent of internal groupware users.
                
                [Mission]
                Analyze the user's current input and previous conversation context to classify them into one of the following categories:
                1. OVERTIME_ONEDAY: Applying for overtime/holiday work on a specific date (Note: Simple inquiry or confirmation does not apply)
                2. OVERTIME_MONTHLY: Batch application for overtime/holiday work for a specific month (Note: Simple inquiry or confirmation does not apply)
                3. VACATION: Applying for leave, annual leave, half-day leave, or compensatory leave (Note: Simple inquiry or confirmation does not apply)
                4. EMAIL_SUMMARY: Inquiry or summary of received emails (e.g., "Summarize recent emails", "Show my email list", "Check email content", "이메일 요약해줘").
                5. POLICY: Inquiry about internal regulations, guidelines, manuals, rules, or standards (e.g., "vacation rule", "leave policy", "규정 확인") (Requires RAG)
                6. GENERAL: HR, audit, general conversation, checking/confirming attendance information, or cases that do not fall into the above categories.
                
                [Important Constraints]
                - If the user is asking about "rules", "policies", "standards", or "how to" regarding HR or company procedures (e.g., "leave rule", "vacation policy"), classify as 'POLICY'.
                - If the user gives a positive/agreeing response like 'yes', 'proceed', 'submit it', 'okay', 'confirm', or requests to proceed with approval, identify which application/approval process was being discussed in the previous conversation and select that category.
                - If the user simply asks to "show", "tell", or "check" their own overtime hours or commute records, it should be classified as 'GENERAL'.
                - For example, if 'overtime hours were calculated' or 'vacation dates were checked' just before and the AI asked 'Shall I submit it?', you must accurately classify it as 'OVERTIME_ONEDAY' or 'VACATION' at the moment of user's consent.
                - Output ONLY the category name.
                """
        ).build();
        this.stateManager = stateManager;

        // TODO: 실제 환경에서는 DB나 YAML에서 읽어와 Registry를 초기화합니다.
        initSOPRegistry();
    }

    /**
     * 🚀 [고도화됨] LLM Fallback 및 주제 전환 안내가 포함된 비동기 라우터
     */
    public Mono<AgentWorkflowSOP> determineSop(String userMessage, String roomId) {
        long startTime = System.currentTimeMillis();
        return stateManager.getState(roomId)
            .flatMap(stateInfo -> {
                String state = stateInfo.state();
                log.info("[IntentRouter] 소요시간(조회): {}ms, 상태: {}", (System.currentTimeMillis() - startTime), state);

                if ("DB_ERROR".equals(state)) {
                    AgentWorkflowSOP general = sopRegistry.get("GENERAL");
                    String warning = "\n\n[주의] 현재 시스템 문제로 대화 맥락 유지(상태 관리)가 원활하지 않을 수 있습니다. 중요한 상신 전에는 다시 한번 확인해 주세요.";
                    return Mono.just(new AgentWorkflowSOP(general.intentId(), general.rules() + warning, general.requiresRag()));
                }

                if ("EMPTY".equals(state)) {
                    return Mono.empty();
                }
                AgentWorkflowSOP fastIntent = fastMatch(userMessage);
                if (fastIntent != null) {
                    String expectedState = fastIntent.intentId() + "_WAITING";
                    if (!state.startsWith(expectedState) && !"OVERTIME_MONTHLY".equals(fastIntent.intentId())) {
                        log.info("사용자 의도 변경 감지 - 기존 상태({}) 초기화 후 {} 플로우 진입", state, fastIntent.intentId());
                        return stateManager.clearState(roomId)
                            .then(applyStateAndReturn(roomId, fastIntent))
                            .map(sop -> injectSwitchNotice(sop, state));
                    }
                }

                // 2. 취소 의도 파악
                if (isCancelIntent(userMessage)) {
                    log.info("사용자 취소 요청 - 상태 초기화");
                    return stateManager.clearState(roomId)
                        .thenReturn(sopRegistry.get("GENERAL"));
                }

                // 3. 현재 진행 중인 다중 턴 상태 처리 (Generic WAITING 처리)
                if (state.contains("_WAITING")) {
                    String baseIntentId = state.split(":")[0].replace("_WAITING", "");

                    AgentWorkflowSOP baseSop = sopRegistry.getOrDefault(baseIntentId, sopRegistry.get("GENERAL"));

                    // 신선도 체크: 30분 이상 지났다면 프롬프트에 안내 주입
                    if (stateInfo.updatedAt() != null) {
                        java.time.Duration duration = java.time.Duration.between(stateInfo.updatedAt(), java.time.OffsetDateTime.now());
                        if (duration.toMinutes() >= 30) {
                            log.info("[IntentRouter] 30분 경과 맥락 감지 - 안내 문구 주입");
                            baseSop = injectStaleNotice(baseSop, duration.toMinutes());
                        }
                    }

                    if (isConfirmIntent(userMessage)) {
                        log.info("사용자 승인 확인 - {} 결재 상신 플로우로 넘기고 상태 초기화", baseIntentId);
                        return stateManager.clearState(roomId)
                            .thenReturn(injectConfirmNotice(baseSop));
                    }
                    return Mono.just(baseSop);
                }

                return Mono.just(sopRegistry.get("GENERAL"));
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.info("상태 없음, 일반 라우터 가동");
                AgentWorkflowSOP matchedSop = fastMatch(userMessage);
                if (matchedSop != null) {
                    return applyStateAndReturn(roomId, matchedSop);
                } else {
                    return classifyLlmAsync(userMessage)
                        .flatMap(sop -> applyStateAndReturn(roomId, sop));
                }
            }))
            .doOnNext(sop -> log.info("[IntentRouter] 최종 결정: {} (총 소요시간: {}ms)", sop.intentId(), (System.currentTimeMillis() - startTime)));
    }

    // 승인 시 메인 LLM에게 즉시 실행 지침을 주입하여 결정 속도 향상
    private AgentWorkflowSOP injectConfirmNotice(AgentWorkflowSOP sop) {
        String confirmGuide = "\n\n[안내] 사용자가 방금 요청을 최종 승인(진행)했습니다. 추가 질문 없이 즉시 관련 MCP 도구를 호출하여 처리를 완료하세요.";
        return new AgentWorkflowSOP(sop.intentId(), sop.rules() + confirmGuide, sop.requiresRag());
    }

    // 1차 고속 라우터: 키워드 기반 분류 (지연 방지를 위해 최대한 여기서 걸러야 함)
    private AgentWorkflowSOP fastMatch(String text) {
        String cleanText = text.replaceAll("[\\s&,]+", "").toLowerCase();

        // 긍정형 대답은 키워드 매칭에서 제외하여 WAITING 로직을 타게 함
        if (isConfirmIntent(text)) return null;

        // 1. [EMAIL_SUMMARY] 이메일 요약/조회 관련
        if (cleanText.contains("이메일") || cleanText.contains("메일") || cleanText.contains("전자우편") || cleanText.contains("수신함") ||
            cleanText.contains("email") || cleanText.contains("mail") || cleanText.contains("inbox") ||
            cleanText.contains("邮件") || cleanText.contains("邮箱") || cleanText.contains("收件箱") ||
            cleanText.contains("メール") || cleanText.contains("受信箱") ||
            cleanText.contains("thưđiệntử") || cleanText.contains("hộpthư")) {
            return sopRegistry.get("EMAIL_SUMMARY");
        }

        // 2. [POLICY] 사내 규정/지침 관련 (가장 포괄적이며 우선순위 높음)
        if (cleanText.contains("규정") || cleanText.contains("사규") || cleanText.contains("지침") || 
            cleanText.contains("매뉴얼") || cleanText.contains("가이드") || cleanText.contains("조항") ||
            cleanText.contains("policy") || cleanText.contains("regulation") || cleanText.contains("manual") || 
            cleanText.contains("guide") || cleanText.contains("rule") || cleanText.contains("standard") || 
            cleanText.contains("howto") || cleanText.contains("instruction") || cleanText.contains("procedure") ||
            cleanText.contains("规定") || cleanText.contains("規定") || cleanText.contains("マニュアル") || 
            cleanText.contains("ルール") || cleanText.contains("quyđịnh") || cleanText.contains("hướngdẫn")) {
            return sopRegistry.get("POLICY");
        }

        // "조회", "보여줘", "알려줘", "확인" 등 단순 조회성 키워드가 포함된 경우 (POLICY가 아닐 때만 제외)
        if (cleanText.contains("조회") || cleanText.contains("보여줘") || cleanText.contains("알려줘") ||
            cleanText.contains("얼마나") || cleanText.contains("확인해") || cleanText.contains("궁금") ||
            cleanText.contains("show") || cleanText.contains("tell") || cleanText.contains("check") ||
            cleanText.contains("howmany") || cleanText.contains("verify") || cleanText.contains("view") ||
            cleanText.contains("見せて") || cleanText.contains("教えて") || cleanText.contains("確認") ||
            cleanText.contains("xem") || cleanText.contains("cho") || cleanText.contains("biết")) {
            return null;
        }

        // 2. [OVERTIME] 잔업/특근 신청 관련
        if (cleanText.contains("ot") || cleanText.contains("잔업") || cleanText.contains("특근") ||
            cleanText.contains("야근") || cleanText.contains("초과근무") || cleanText.contains("연장근무") ||
            cleanText.contains("overtime") || cleanText.contains("extrawork") || cleanText.contains("nightshift") || 
            cleanText.contains("workinglate") || cleanText.contains("加班") || cleanText.contains("殘業") || cleanText.contains("残業") || 
            cleanText.contains("làmthêm") || cleanText.contains("tăngca")) {

            if (cleanText.contains("월") || cleanText.contains("이번달") || cleanText.contains("이전달") || cleanText.contains("저번달") || cleanText.contains("지난달") ||
                cleanText.contains("month") || cleanText.contains("月") || cleanText.contains("今月") || cleanText.contains("tháng")) {
                if (cleanText.contains("신청") || cleanText.contains("상신") ||
                    cleanText.contains("apply") || cleanText.contains("submit") ||
                    cleanText.contains("申请") || cleanText.contains("提交") ||
                    cleanText.contains("申請") || cleanText.contains("đăngký")) {
                    return sopRegistry.get("OVERTIME_MONTHLY");
                }
                return null;
            }
            return sopRegistry.get("OVERTIME_ONEDAY");
        }

        // 3. [VACATION] 휴가/연차 신청 관련
        if (cleanText.contains("휴가") || cleanText.contains("연차") || cleanText.contains("반차") ||
            cleanText.contains("반반차") || cleanText.contains("보상휴가") || cleanText.contains("결근") ||
            cleanText.contains("병가") || cleanText.contains("조퇴") || cleanText.contains("경조사") ||
            cleanText.contains("vacation") || cleanText.contains("leave") || cleanText.contains("holiday") || 
            cleanText.contains("dayoff") || cleanText.contains("off") || cleanText.contains("break") || 
            cleanText.contains("sick") || cleanText.contains("absence") ||
            cleanText.contains("请假") || cleanText.contains("休假") ||
            cleanText.contains("休暇") || cleanText.contains("有休") || cleanText.contains("休み") ||
            cleanText.contains("nghỉ") || cleanText.contains("vắngmặt")) {
            return sopRegistry.get("VACATION");
        }

        return null;
    }

    // 특정 SOP로 분류되었을 때 상태를 잠그는(Set) 역할
    private Mono<AgentWorkflowSOP> applyStateAndReturn(String roomId, AgentWorkflowSOP sop) {
        if ("OVERTIME_ONEDAY".equals(sop.intentId()) || "VACATION".equals(sop.intentId())) {
            String stateName = sop.intentId() + "_WAITING";
            log.info("🔒 [상태 잠금] {} 방에 {} 상태 부여", roomId, stateName);
            return stateManager.setState(roomId, stateName)
                .thenReturn(sop);
        }
        return Mono.just(sop);
    }

    // 오래된 맥락(Stale Context)에 대한 가이드 주입
    private AgentWorkflowSOP injectStaleNotice(AgentWorkflowSOP sop, long minutes) {
        String staleGuide = String.format("""
            \n
            [주의] 사용자와의 마지막 대화로부터 약 %d분 정도가 지났습니다. \
            사용자가 이전 작업을 잊었을 수 있으므로, 답변 시작 시 "이전의 %s 신청을 계속 진행할까요?"와 같이 \
            현재 상황을 가볍게 리마인드하며 대화를 시작해 주세요.""", minutes, sop.intentId());
        return new AgentWorkflowSOP(sop.intentId(), sop.rules() + staleGuide, sop.requiresRag());
    }

    // 주제 전환 시 AI에게 사용자 알림을 유도하는 프롬프트 주입
    private AgentWorkflowSOP injectSwitchNotice(AgentWorkflowSOP sop, String oldState) {
        if (oldState == null) return sop;
        String baseState = oldState.split(":")[0].replace("_WAITING", "");
        String notice = String.format("""
            \n[알림] 사용자가 기존 '%s' 관련 작업을 중단하고 새로운 요청을 했습니다. \
            기존 작업이 중단되었음을 언급하며 새로운 요청을 친절히 도와주세요.""", baseState);
        return new AgentWorkflowSOP(sop.intentId(), sop.rules() + notice, sop.requiresRag());
    }

    // LLM 분류 비동기 처리
    private Mono<AgentWorkflowSOP> classifyLlmAsync(String userMessage) {
        return Mono.fromCallable(() -> classifyLlm(userMessage))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(java.time.Duration.ofSeconds(4)) // 타임아웃을 4초로 확장
            .onErrorResume(e -> {
                log.error("[IntentRouter] LLM 분류 실패 또는 지연 (결과: GENERAL): {}", e.getMessage());
                return Mono.just(sopRegistry.get("GENERAL"));
            });
    }

    private AgentWorkflowSOP classifyLlm(String userMessage) {
        try {
            IntentClassification result = routerClient.prompt()
                .user(userMessage)
                .call()
                .entity(IntentClassification.class);
            return sopRegistry.getOrDefault(Objects.requireNonNull(result).category(), sopRegistry.get("GENERAL"));
        } catch (Exception e) {
            log.error("[IntentRouter] LLM 분류 에러: {}", e.getMessage());
            return sopRegistry.get("GENERAL");
        }
    }

    private boolean isCancelIntent(String text) {
        String clean = text.replaceAll("\\s+", "").toLowerCase();
        return clean.contains("아니") || clean.contains("취소") || clean.contains("됐어") ||
            clean.contains("하지마") || clean.contains("관둘래") || clean.contains("안할래") ||
            clean.contains("정지") || clean.contains("멈춰") ||
            clean.contains("no") || clean.contains("cancel") || clean.contains("stop") ||
            clean.contains("いいえ") || clean.contains("キャンセル") || clean.contains("やめて") ||
            clean.contains("không") || clean.contains("hủy");
    }

    private boolean isConfirmIntent(String text) {
        String clean = text.replaceAll("\\s+", "").toLowerCase();
        return clean.contains("응") || clean.contains("어") || clean.contains("맞아") ||
            clean.contains("진행해") || clean.contains("상신해") || clean.contains("해줘") ||
            clean.contains("그래") || clean.contains("좋아") || clean.contains("오케이") ||
            clean.contains("ok") || clean.contains("확인") ||
            clean.contains("yes") || clean.contains("proceed") || clean.contains("submit") ||
            clean.contains("はい") || clean.contains("進めて") || clean.contains("了解") ||
            clean.contains("có") || clean.contains("đồng ý");
    }

    private void initSOPRegistry() {
        // 1. 단일 날짜 전용 플로우 (기존과 동일 + 명확화)
        sopRegistry.put(
            "OVERTIME_ONEDAY", new AgentWorkflowSOP(
                "OVERTIME_ONEDAY", """
                     당신은 현재 [단일 날짜 잔업/특근 신청 워크플로우]를 수행 중입니다.
                     아래의 절차(<step>)와 제약사항(<constraint>)을 엄격하게 준수하세요.
                
                     <step>
                      1. 시스템 정보의 '현재 날짜 및 시간'을 기준으로 사용자가 요청한 날짜(예: 내일, 모레 등)를 정확한 날짜(YYYY-MM-DD)로 변환하여 잔업 날짜(ot_date)를 파악합니다. 사용자가 날짜를 명시하지 않았다면 다시 사용자에게 정확한 ot_date를 물어보게 합니다.
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
                     사용자의 요청을 분석하여 'request_for_leave' 도구 형식에 맞게 정보를 추출하세요.
                     아래의 절차(<step>)를 엄격하게 준수하세요.
                
                     <step>
                      1. 시스템 정보의 '현재 날짜 및 시간'을 기준으로 사용자가 요청한 날짜(예: 내일, 모레 등)를 정확한 날짜(YYYY-MM-DD)로 변환하여 휴가 날짜(leave_date)를 파악합니다.
                      2. 휴가 종류(leave_type)를 파악합니다.
                      3. 만약 '반차'나 '반일보상휴가'라면 반드시 '오전'인지 '오후'인지(half_day_type)를 확인하세요.
                      4. 만약 '반반차'라면 반드시 '시작 시간'(start_time)을 확인하세요.
                      5. 정보가 하나라도 누락되었다면 사용자에게 질문하여 보충하세요. (예: "오후반차이신가요, 오전반차이신가요?")
                      6. 모든 정보가 파악되면 "이 내용으로 휴가를 신청할까요?"라고 최종 확인합니다.
                      7. 승인 시에만 도구를 호출하되, '오후반차'는 leave_type="반차", half_day_type="오후"로 나누어 전달합니다.
                     </step>
                """, false
            )
        );

        sopRegistry.put(
            "EMAIL_SUMMARY", new AgentWorkflowSOP(
                "EMAIL_SUMMARY", """
                     당신은 현재 [이메일 요약 및 조회 워크플로우]를 수행 중입니다.
                     이 작업은 데이터 양에 따라 시간이 오래 걸릴 수 있으므로, 사용자의 편의를 위해 '비동기 작업 접수' 방식으로 안내하고 처리해야 합니다.
                     아래의 지침(<instruction>)과 포맷(<format>)을 준수하세요.
                
                     <instruction>
                      1. 사용자의 요청 의도에 따라 다음 3가지 중 적절한 MCP 도구를 호출합니다.
                         - [기능 1] 단일 메일 요약 (`get_single_email_detail` 호출)
                         - [기능 2] 여러 메일 요약 (`get_multiple_emails_with_summary` 호출)
                         - [기능 3] 메일 목록 조회 (`get_email_list_simple` 호출)
                
                      2. 비동기 처리 지침:
                         - 당신은 백그라운드에서 실행되는 AI입니다. 사용자에게 "접수되었습니다"와 같은 안내를 할 필요가 없습니다.
                         - 도구를 실행하여 얻은 이메일 요약/목록 결과를 바탕으로 아래 <format>에 맞춰 '최종 답변'만 생성하세요.
                         - 당신이 생성한 답변은 자동으로 시스템에 저장되어 사용자에게 알림으로 전달됩니다.
                
                      3. 응답 공통 규칙:
                         - 모든 응답에는 '보낸 사람', '제목', '시간' 정보가 기본적으로 포함되어야 합니다.
                         - 기능 3(목록 조회)의 경우 제목은 요약하지 않고 그대로 리턴하며, 내용이 길면 최대 300자까지만 축약합니다.
                         - 첨부파일 URL은 반드시 하이퍼링크 형식([파일명](URL))으로 표현하세요.
                     </instruction>
                
                     <format>
                      의도에 따라 가독성 좋은 마크다운 형식을 사용하세요.
                
                      [접수 안내 메시지]
                      > 📥 **이메일 요약 요청이 접수되었습니다.**
                      > 내용이 많을 경우 시간이 다소 소요될 수 있습니다. 완료 시 알림으로 알려드릴게요!
                
                      [결과 출력 형식]
                      - 단일/여러 메일 요약:
                        ### 📝 이메일 요약 결과
                        - **제목**: [원본 제목]
                        - **보낸이/시간**: [이름] / [시간]
                        - **요약**: [핵심 내용 요약]
                        - **첨부파일**: [파일명](URL) (없으면 '없음' 표시)
                
                      - 메일 목록 조회:
                        ### 📧 이메일 목록
                        - **[시간]** [원본 제목] (보낸이: [이름])
                        - **내용 요약(300자 내)**: [내용...]
                        - **첨부파일**: [파일명](URL) (없으면 '없음' 표시)
                     </format>
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
    record IntentClassification(String category, double confidence) {
    }
}