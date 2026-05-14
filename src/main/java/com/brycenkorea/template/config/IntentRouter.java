package com.brycenkorea.template.config;

import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.util.ChatPromptUtil;
import com.brycenkorea.template.util.ChatStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class IntentRouter {

    private final ChatClient routerClient;
    private final ChatStateManager stateManager;
    private final Map<String, AgentWorkflowSOP> sopRegistry = new ConcurrentHashMap<>();
    private final List<RouteRule> fastMatchRules = List.of(
        new RouteRule("EMAIL_SUMMARY",
            List.of(
                "메일", "이메일", "전자우편", "수신함",
                "email", "mail", "inbox",
                "メール", "Eメール", "受信箱",
                "email", "thư", "thu", "hộp thư", "hop thu", "hộp thư đến", "hop thu den"
            ),
            List.of(
                "요약", "보여줘", "목록", "조회", "확인", "수신", "읽어", "검색", "알려줘",
                "summary", "summarize", "show", "list", "view", "check", "read", "search", "received",
                "要約", "見せて", "一覧", "確認", "受信", "読む", "検索",
                "tóm tắt", "tom tat", "hiển thị", "hien thi", "xem", "danh sách", "danh sach", "kiểm tra", "kiem tra", "nhận", "nhan"
            )),
        new RouteRule("POLICY",
            List.of(
                "규정", "사규", "지침", "매뉴얼", "가이드", "조항",
                "policy", "regulation", "manual", "guide", "rule", "standard", "howto", "instruction", "procedure",
                "規定", "社規", "規則", "指針", "マニュアル", "ガイド", "手順",
                "quy định", "quy dinh", "chính sách", "chinh sach", "sổ tay", "so tay", "hướng dẫn", "huong dan", "nội quy", "noi quy", "quy trình", "quy trinh"
            ),
            List.of()),
        new RouteRule("OVERTIME_MONTHLY",
            List.of(
                "야근", "연장근무", "초과근무", "ot",
                "overtime", "extra work", "night shift", "working late",
                "残業", "時間外勤務", "夜勤",
                "làm thêm", "lam them", "tăng ca", "tang ca"
            ),
            List.of(
                "이번달", "이달", "전월", "지난달", "월간", "월별", "신청", "상신", "등록",
                "month", "monthly", "apply", "submit", "request", "register",
                "今月", "先月", "月", "申請", "提出", "登録",
                "tháng", "thang", "hàng tháng", "hang thang", "đăng ký", "dang ky", "nộp", "nop", "xin"
            )),
        new RouteRule("OVERTIME_ONEDAY",
            List.of(
                "야근", "연장근무", "초과근무", "ot",
                "overtime", "extra work", "night shift", "working late",
                "残業", "時間外勤務", "夜勤",
                "làm thêm", "lam them", "tăng ca", "tang ca"
            ),
            List.of(
                "신청", "상신", "등록", "할래", "해줘",
                "apply", "submit", "request", "register",
                "申請", "提出", "登録",
                "đăng ký", "dang ky", "nộp", "nop", "xin"
            )),
        new RouteRule("WORK_PLAN",
            List.of(
                "근무계획", "계획상신", "계획서",
                "work plan", "workplan",
                "勤務計画", "勤務表", "計画申請",
                "kế hoạch làm việc", "ke hoach lam viec", "lịch làm việc", "lich lam viec"
            ),
            List.of(
                "상신", "신청", "등록", "작성", "제출",
                "apply", "submit", "request", "register", "create",
                "申請", "提出", "登録", "作成",
                "đăng ký", "dang ky", "nộp", "nop", "tạo", "tao", "lập", "lap"
            )),
        new RouteRule("VACATION",
            List.of(
                "휴가", "연차", "반차", "반반차", "보상휴가", "결근", "병가", "조퇴", "경조휴가",
                "vacation", "annual leave", "half day", "leave", "holiday", "day off", "sick leave", "absence", "early leave",
                "休暇", "有給", "半休", "病休", "早退", "欠勤",
                "nghỉ", "nghi", "nghỉ phép", "nghi phep", "phép", "phep", "nghỉ bệnh", "nghi benh", "nửa ngày", "nua ngay"
            ),
            List.of(
                "신청", "상신", "쓸래", "등록", "사용", "가고싶", "내고싶",
                "apply", "submit", "request", "register", "use", "take",
                "申請", "提出", "登録", "使いたい", "取りたい", "出したい",
                "đăng ký", "dang ky", "xin", "nộp", "nop", "sử dụng", "su dung", "muốn nghỉ", "muon nghi"
            )),
        new RouteRule("MEETING_ROOM",
            List.of(
                "회의실", "미팅룸", "공유물", "리브라", "에리스", "캐프리콘", "제미나이", "미라이", "스콜피오", "리오", "파이시스", "이클립스", "santafe", "싼타페", "산타페",
                "meeting room", "meetingroom", "conference room", "libra", "eris", "capricorn", "gemini", "mirai", "scorpio", "leo", "pisces", "eclipse", "shared asset",
                "会議室", "ミーティングルーム", "共有物", "リブラ", "エ리스", "カプリコーン", "ジェ미ナイ", "ミライ", "スコルピオ", "リオ", "パイシス", "イクリプス",
                "phòng họp", "phong hop", "phòng họp libra", "phong hop libra", "phòng họp eris", "phong hop eris", "tài sản chung", "tai san chung"
            ),
            List.of(
                "예약", "잡아줘", "대여", "빈방", "확인", "조회", "빌려", "내역", "현황", "확인해줘", "보여줘",
                "reserve", "reservation", "book", "rent", "available", "vacancy", "check",
                "予約", "借りる", "空き", "空いて", "空室", "確認",
                "đặt", "dat", "đặt phòng", "dat phong", "đăng ký", "dang ky", "còn trống", "con trong", "trống", "trong", "mượn", "muon", "thuê", "thue"
            ))
            );

    public IntentRouter(ChatClient.Builder builder, ChatStateManager stateManager) {
        // 라우터 전용으로 가볍고 빠른 모델을 세팅합니다.
        this.routerClient = builder.defaultSystem(ChatPromptUtil.getIntentRouterSystemPrompt()).build();
        this.stateManager = stateManager;

        initSOPRegistry();
    }

    // 1차 고속 라우터: 키워드 기반 분류 (지연 방지를 위해 최대한 여기서 걸러야 함)
    private static String cleanKeyword(String text) {
        return text.replaceAll("[\\s&,]+", "").toLowerCase(Locale.ROOT);
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

                // 1. 고속 키워드 매칭 (즉시 전환 감지)
                AgentWorkflowSOP fastIntent = fastMatch(userMessage);
                if (fastIntent != null && isDifferentIntent(state, fastIntent.intentId())) {
                    log.info("사용자 의도 변경 감지(FastMatch) - {} -> {}", state, fastIntent.intentId());
                    return handleIntentSwitch(roomId, state, fastIntent);
                }

                // 2. 취소 의도 파악
                if (isCancelIntent(userMessage)) {
                    log.info("사용자 취소 요청 - 상태 초기화");
                    return stateManager.clearState(roomId).thenReturn(sopRegistry.get("GENERAL"));
                }

                // 3. 진행 중인 상태 처리 (Generic WAITING 처리)
                if (state.contains("_WAITING")) {
                    String baseIntentId = state.split(":")[0].replace("_WAITING", "");
                    long minutes = getElapsedMinutes(stateInfo);

                    if (minutes < 30 && isConfirmIntent(userMessage)) {
                        log.info("사용자 승인 확인 - {} 결재 상신", baseIntentId);
                        return stateManager.clearState(roomId)
                            .thenReturn(injectConfirmNotice(sopRegistry.getOrDefault(baseIntentId, sopRegistry.get("GENERAL"))));
                    }

                    log.info("[IntentRouter] 상태 유지 중 LLM 재확인 시도 (경과: {}분)", minutes);
                    return classifyLlmAsync(userMessage)
                        .flatMap(newSop -> {
                            if (isDifferentIntent(state, newSop.intentId()) && !"GENERAL".equals(newSop.intentId())) {
                                log.info("사용자 의도 변경 감지(LLM) - {} -> {}", state, newSop.intentId());
                                return handleIntentSwitch(roomId, state, newSop);
                            }

                            // 동일 의도이거나 GENERAL인 경우 기존 맥락 유지
                            AgentWorkflowSOP currentSop = sopRegistry.getOrDefault(baseIntentId, sopRegistry.get("GENERAL"));
                            if (minutes >= 30) {
                                currentSop = injectStaleNotice(currentSop, minutes);
                                // 30분 경과 후라도 승인 의도라면 상신 처리
                                if (isConfirmIntent(userMessage)) {
                                    return stateManager.clearState(roomId).thenReturn(injectConfirmNotice(currentSop));
                                }
                            }
                            return Mono.just(currentSop);
                        });
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

    private boolean isDifferentIntent(String currentState, String newIntentId) {
        if (currentState == null || newIntentId == null) return false;
        String baseIntent = currentState.split(":")[0].replace("_WAITING", "");
        return !baseIntent.equals(newIntentId);
    }

    private Mono<AgentWorkflowSOP> handleIntentSwitch(String roomId, String oldState, AgentWorkflowSOP newSop) {
        if ("OVERTIME_MONTHLY".equals(newSop.intentId())) {
            return stateManager.clearState(roomId).thenReturn(newSop);
        }
        return stateManager.clearState(roomId)
            .then(applyStateAndReturn(roomId, newSop))
            .map(sop -> injectSwitchNotice(sop, oldState));
    }

    private long getElapsedMinutes(ChatStateManager.StateInfo stateInfo) {
        if (stateInfo.updatedAt() == null) return 0;
        return java.time.Duration.between(stateInfo.updatedAt(), java.time.OffsetDateTime.now()).toMinutes();
    }

    // 승인 시 메인 LLM에게 즉시 실행 지침을 주입하여 결정 속도 향상
    private AgentWorkflowSOP injectConfirmNotice(AgentWorkflowSOP sop) {
        String confirmGuide = "\n\n[안내] 사용자가 방금 요청을 최종 승인(진행)했습니다. 추가 질문 없이 즉시 관련 MCP 도구를 호출하여 처리를 완료하세요.";
        return new AgentWorkflowSOP(sop.intentId(), sop.rules() + confirmGuide, sop.requiresRag());
    }

    private AgentWorkflowSOP fastMatch(String text) {
        String cleanText = cleanKeyword(text);

        // 긍정형 대답은 기존 WAITING 확인 로직에서 처리되도록 fast match 대상에서 제외한다.
        if (isConfirmIntent(text)) return null;

        for (RouteRule rule : fastMatchRules) {
            if (rule.isMatch(cleanText)) {
                return sopRegistry.get(rule.intentId());
            }
        }

        return null;
    }

    // 특정 SOP로 분류되었을 때 상태를 잠그는(Set) 역할
    private Mono<AgentWorkflowSOP> applyStateAndReturn(String roomId, AgentWorkflowSOP sop) {
        if ("OVERTIME_ONEDAY".equals(sop.intentId()) || "VACATION".equals(sop.intentId()) || "WORK_PLAN".equals(sop.intentId()) || "MEETING_ROOM".equals(sop.intentId())) {
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

    private Mono<AgentWorkflowSOP> classifyLlmAsync(String userMessage) {
        return Mono.fromCallable(() -> classifyLlm(userMessage))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(java.time.Duration.ofSeconds(7)) // LLM 분류 안정성을 위해 타임아웃 7초로 연장
            .onErrorResume(e -> {
                log.error("[IntentRouter] LLM 분류 실패 (결과: GENERAL): {}", e.getMessage());
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
                      5. 도구 실행 결과로 반환된 'message'를 출력하고, 다음 줄에 도구 결과의 'url'을 활용하여 **🔗 [결재 문서 확인](url)** 형태의 마크다운 하이퍼링크를 반드시 제공하세요. 이때 링크 앞뒤에 다른 문자를 붙이지 말고 반드시 별도의 줄에 작성하세요.
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
                     3. 상신 도구의 실행 결과로 반환된 'message'를 안내하고, 다음 줄에 'url' 정보를 활용하여 **🔗 [결재 문서 확인](url)** 형태의 마크다운 하이퍼링크를 별도의 줄에 제공하여 대화를 종료합니다.
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
                      8. 도구 실행 결과로 반환된 'message'를 사용자에게 전달하고, 다음 줄에 'url' 정보를 활용하여 **🔗 [결재 문서 확인](url)** 형태의 마크다운 하이퍼링크를 별도의 줄에 포함하여 사용자가 바로 접근할 수 있도록 하세요.
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
                         - **가장 상단에 조회된 메일의 총 개수를 표시하세요. (예: 총 3건의 메일이 조회되었습니다.)**
                         - 모든 응답에는 '보낸 사람', '제목', '시간' 정보가 기본적으로 포함되어야 합니다.
                         - **항목이 여러 개인 경우 반드시 1, 2, 3... 순서로 넘버링을 적용하세요.**
                         - 기능 3(목록 조회)의 경우 제목은 요약하지 않고 그대로 리턴하며, 내용이 길면 최대 300자까지만 축약합니다.
                         - 첨부파일 URL은 반드시 하이퍼링크 형식([파일명](URL))으로 표현하세요.
                         - **항목 간에는 줄바꿈을 2번 적용하여 시각적 여백을 확보하고, 상세 정보는 들여쓰기를 적용하세요.**
                      </instruction>
                
                      <format>
                      의도에 따라 가독성 좋은 마크다운 형식을 사용하세요.
                
                      [결과 출력 형식]
                      - 단일/여러 메일 요약:
                        ### 📝 이메일 요약 결과 (총 N건)
                
                        1. **제목**: [원본 제목]
                           - **보낸이/시간**: [이름] / [시간]
                           - **요약**:
                             > [핵심 내용 요약 - 가독성을 위해 인용구와 들여쓰기 적용]
                           - **첨부파일**: [파일명](URL) (없으면 '없음' 표시)
                
                      - 메일 목록 조회:
                        ### 📧 이메일 목록 (총 N건)
                
                        1. **[시간]** [원본 제목]
                           - **보낸이**: [이름]
                           - **내용 요약(300자 내)**: [내용...]
                           - **첨부파일**: [파일명](URL) (없으면 '없음' 표시)
                        --- (메일 항목이 여러 개인 경우 구분선 사용)
                     </format>
                """, false
            )
        );

        sopRegistry.put(
            "WORK_PLAN", new AgentWorkflowSOP(
                "WORK_PLAN", """
                     당신은 현재 [근무계획 수립 워크플로우]를 수행 중입니다.
                     이 작업은 사용자가 요청한 연/월의 근무계획을 자동으로 생성하고 **즉시 상신**하는 작업입니다.
                     (참고: 시스템상 임시저장 시 자동으로 상신된 것으로 간주될 수 있으므로 사용자에게 사전 안내가 필수입니다.)
                
                     <step>
                     1. 대화 문맥에서 사용자가 원하는 연도(year)와 월(month)을 파악하세요. 언급이 없다면 시스템의 '현재 날짜 및 시간'을 기준으로 다음 달을 기본값으로 사용합니다.
                     2. 사용자에게 "N년 N월 근무계획을 수립하여 즉시 상신할까요?"라고 명확하게 확인을 받으세요.
                     3. 사용자가 동의(승인)하면, 그제서야 'request_work_plan_approval' 도구를 호출하세요.
                     4. 도구 실행 결과로 반환된 'message'를 사용자에게 전달하고, 다음 줄에 'url' 정보를 활용하여 **🔗 [결재 문서 확인](url)** 형태의 마크다운 하이퍼링크를 별도의 줄에 포함하여 안내를 종료하세요.
                     </step>
                
                     <constraint>
                     - STEP 2를 수행한 후에는 반드시 [STOP] 하고 사용자의 대답을 기다려야 합니다.
                     - 사용자의 명시적인 '승인' 응답이 존재하기 전까지는 절대로 도구를 호출하지 마십시오.
                     </constraint>
                """, false
            )
        );

        sopRegistry.put(
            "MEETING_ROOM", new AgentWorkflowSOP(
                "MEETING_ROOM", """
                     당신은 현재 [공유물 예약 워크플로우]를 수행 중입니다.
                     아래의 지침(<instruction>)과 포맷(<format>)을 엄격하게 준수하세요.
                
                     <instruction>
                      1. **의도 구분**: 사용자의 요청이 단순히 '예약 현황/목록 조회'인지, 아니면 '새로운 예약 신청'인지 명확히 구분하세요.
                         - **조회 요청**: 단순히 특정 날짜나 공유물의 예약 상태를 보고 싶어 하는 경우, 'fetch_meeting_room_reservations' 도구를 호출하여 목록을 보여주는 것으로 완료합니다.
                         - **예약 요청**: 새로운 예약을 잡으려는 경우, 아래의 필수 정보를 수집해야 합니다.
                      2. **정보 수집 (예약 시)**: 예약 날짜(reservation_date), 공유물명(room_name), 시작/종료 시간(start_time, end_time), 회의 제목(title)은 **필수 항목**입니다.
                         - **공유물명(room_name) 필수 체크**: 사용자가 아래 목록 중 하나를 명시했는지 확인하세요.
                           (리브라, 에리스, 캐프리콘, 제미나이, 미라이, 스콜피오, 리오1~4, 파이시스1~4, 이클립스, SANTAFE(231호5640))
                         - 만약 '공유물'이라는 범주만 언급하거나 목록에 없는 이름을 말하면, 위의 목록을 안내하며 구체적인 공유물명을 선택하도록 요청하세요.
                         - 정보가 누락되었다면 **[정보 요청 템플릿]**을 사용하세요.
                      3. **현황 확인**: 예약 신청 전 반드시 'fetch_meeting_room_reservations' 도구를 호출하여 중복 여부를 확인합니다.
                      4. **예약 가능 시**: 아래의 **[최종 확인 템플릿]**을 사용하여 사용자에게 예약 의사를 묻고 [STOP] 합니다.
                      5. **중복 발생 시 처리**:
                         - 만약 요청한 시간대(또는 기간 중 특정 날짜)에 이미 예약이 있다면, **절대로 그냥 "예약이 어렵다"고만 하지 마십시오.**
                         - 반드시 **'[날짜] [시작시간~종료시간] 회의제목 (예약자)'** 형식으로 기존 예약 리스트를 모두 보여주어야 합니다.
                         - 그 아래에 해당 날짜에 예약 가능한 빈 시간대 리스트를 정확히 계산하여 안내하고, 다른 시간을 선택할지 물어본 후 [STOP] 합니다.
                         - **시간 중복 판단 주의사항**: 본 시스템은 기존 예약의 '종료 시간'과 새로운 요청의 '시작 시간'이 정확히 맞닿아 있는 경우(예: 기존 예약이 11:00~12:00일 때 12:00~13:00으로 신규 요청 시 '12:00'이 겹침)에도
                         **중복으로 처리하여 예약이 불가능**합니다. 따라서 경계 시간이 겹칠 경우 예약이 불가함을 안내하고, 겹치지 않는 다른 시간을 10분 단위로(예: 12:10 시작 등)을 제안하세요.
                      6. **예약 실행**: 사용자가 승인하면 'book_meeting_room' 도구를 호출합니다. 이때 사용자가 제목을 말하지 않았다면 절대로 임의로 '회의' 등으로 입력하지 말고 반드시 제목을 물어봐야 합니다.
                      7. 완료 안내: 예약 성공 시 **[예약 완료 템플릿]**에 맞춰 결과를 출력합니다. 이때 도구 결과의 'url' 정보를 활용하여 **🔗 [예약 내역 확인](url) 형태의 마크다운 하이퍼링크를 별도의 줄에 반드시 포함하세요.
                      </instruction>
                
                     <instruction>
                      - **날짜 계산 철저**: 시스템 정보(`currentDateTime`, `currentDayOfWeek`)를 최우선으로 신뢰하세요.
                        - 예: 시스템이 5월 10일 일요일이라고 하면, 내일은 반드시 5월 11일 월요일입니다. 요일을 임의로 추측하거나 잘못된 달력을 참조하지 마십시오.
                      - 사용자가 "14:50 이후"와 같이 범위를 지정하면, 현재 예약 현황을 보고 마지막 예약 종료 시각부터 24:00까지의 모든 빈 공간을 정확히 계산해야 합니다.
                      - 도구에서 반환된 예약 리스트의 시작/종료 시간을 보고, 겹치지 않는 구간을 수학적으로 꼼꼼히 체크하세요. 추측하여 "예약이 어렵다"고 답하지 마십시오.
                      - 사용자의 명시적인 '승인' 응답이 존재하기 전까지는 절대로 'book_meeting_room' 도구를 호출하지 마십시오.
                     </instruction>
                
                     <format>
                     [정보 요청 템플릿]
                     공유물 예약을 위해 아래 정보를 입력해 주세요.
                     - **공유물 이름**: (필수)
                     - **예약 날짜**: (필수)
                     - **시간 (시작~종료)**: (필수)
                     - **회의 제목**: (필수)
                     ---
                     * (선택) 인원수, 상세 내용
                
                     [최종 확인 템플릿]
                     조회 결과, 해당 시간에 예약이 가능합니다. 이 내용으로 예약을 진행할까요?
                     - **공유물**: {room_name}
                     - **일시**: {start_date}~{end_date} {start_time} ~ {end_time}
                     - **제목**: {title}
                     - **인원**: {people_count} (선택)
                     - **상세 내용**: {description} (선택)
                
                     [예약 완료 템플릿]
                     ✅ **공유물 예약이 완료되었습니다.**
                     - **공유물**: {room_name}
                     - **일시**: {start_date}~{end_date} {start_time} ~ {end_time}
                     - **제목**: {title}
                     - **인원**: {people_count}
                     - **상세 내용**: {description}
                
                     **🔗 [예약 내역 확인](url)
                     </format>
                """, false
            )
        );

        sopRegistry.put("POLICY", new AgentWorkflowSOP("POLICY", "[사내 규정 답변]\n검색된 RAG 문서를 바탕으로 친절하고 정확하게 답변하세요.", true));

        sopRegistry.put("SYSTEM_GUIDE", new AgentWorkflowSOP(
            "SYSTEM_GUIDE", """
                당신은 시스템 기능을 안내하는 가이드입니다. 사용자에게 현재 제공 중인 주요 기능을 카테고리별로 깔끔하게 정리하여 안내하세요.
            
                [핵심 기능 목록]
                1. 🏢 **근태/잔업**: 출퇴근 시간 조회, 일일 연장 근무 및 월간 잔업 일괄 신청
                2. 🏖️ **휴가/연차**: 연차 잔여량 확인, 휴가(연차, 반차, 반반차) 신청 및 결재 상신
                3. 📧 **이메일**: 메일 목록 조회, 중요 메일 내용 요약 및 상세 보기
                4. 📅 **공유물**: 실시간 공유물(회의실 등) 예약 현황 조회 및 예약(리브라, 에리스 등)
                5. ⏰ **스케줄 예약**: 특정 시간 1회성 작업 예약 또는 주기적인 반복 작업 관리
                6. 📚 **사내 규정(RAG)**: 사규, 지침, 매뉴얼 등 사내 문서 기반 질의응답
            
                [응답 지침]
                - **절대로 'MCP', 'Method 명', '도구 이름' 등 기술적인 용어를 사용자에게 노출하지 마세요.**
                - 오직 사용자가 체감할 수 있는 '비즈니스 기능' 관점에서 설명하세요.
                - 각 기능은 대화로 자연스럽게 요청하면 된다는 점을 강조하세요. (예: "내일 오후 반차 신청해줘", "어제 퇴근 시간 알려줘")
                - 사용자가 궁금해하는 기능을 바로 실행해 볼 수 있도록 유도하며 대화를 마무리하세요.
            """, false
        ));

        sopRegistry.put("GENERAL", new AgentWorkflowSOP("GENERAL", """
            당신은 현재 [일반 대화 및 정보 조회 워크플로우]를 수행 중입니다.
            사용자가 특정 신청(상신) 프로세스에 진입하지 않은 상태에서 정보를 궁금해할 경우, 아래의 지침에 따라 적절한 도구를 활용하여 답변하세요.
            
            <capabilities>
            1. 🏢 근태/출퇴근: 'get_team_attendance' 또는 'get_my_commute_record'를 통해 출근/퇴근 시간 및 근무 시간을 조회할 수 있습니다.
            2. 🏖️ 휴가: 'get_vacation_balance'를 통해 잔여 연차 및 휴가 정보를 확인할 수 있습니다.
            3. 📧 이메일: 'get_email_list_simple'을 통해 수신 이메일 목록이나 특정 메일 수신 여부를 확인할 수 있습니다.
            4. 📅 공유물(회의실 등): 'fetch_meeting_room_reservations'를 통해 특정 날짜/공유물의 예약 현황을 조회할 수 있습니다.
            5. ⏰ 스케줄: 사용자가 요청한 작업을 특정 시간에 예약하거나 반복되도록 설정할 수 있습니다.
            6. 📚 규정: 사내 규정이나 지침에 대해 궁금해하면 RAG 기반 검색을 통해 답변할 수 있습니다.
            </capabilities>
            
            <instruction>
            - 사용자의 질문에 가장 적합한 도구를 판단하여 즉시 호출하세요.
            - 정보를 제공한 후에는 "관련하여 신청(신청, 예약, 상신 등)을 도와드릴까요?"와 같이 다음 단계의 액션을 자연스럽게 제안하세요.
            - 특정 도메인(잔업, 휴가 등)의 구체적인 신청 의도가 파악되면 해당 도메인의 전문 에이전트처럼 행동하세요.
            - **응답 시 MCP, 도구 이름, 메서드 명과 같은 기술적 용어는 절대로 노출하지 마세요.**
            </instruction>
            
            판단이 어려우면 추측하지 말고 사용자에게 추가 정보를 요청하세요.
            호출 전 판단이 10초 이상 걸릴 시 사용자에게 추가 정보를 요청하세요.
            """, false));
    }

    private record RouteRule(String intentId, List<String> targets, List<String> actions) {
        private RouteRule {
            targets = targets.stream().map(IntentRouter::cleanKeyword).toList();
            actions = actions.stream().map(IntentRouter::cleanKeyword).toList();
        }

        boolean isMatch(String text) {
            boolean hasTarget = targets.stream().anyMatch(text::contains);
            boolean hasAction = actions.isEmpty() || actions.stream().anyMatch(text::contains);
            return hasTarget && hasAction;
        }
    }

    record IntentClassification(String category, double confidence) {
    }
}
