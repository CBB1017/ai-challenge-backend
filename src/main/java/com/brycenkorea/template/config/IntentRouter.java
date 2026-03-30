package com.brycenkorea.template.config;

import com.brycenkorea.template.contants.AgentWorkflowSOP;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class IntentRouter {

    private final ChatClient routerClient;
    private final Map<String, AgentWorkflowSOP> sopRegistry = new ConcurrentHashMap<>();

    public IntentRouter(ChatClient.Builder builder) {
        // 라우터 전용으로 가볍고 빠른 모델(예: Gemini 1.5 Flash)을 세팅하면 비용과 속도를 아낄 수 있습니다.
        this.routerClient = builder.defaultSystem(
            "당신은 사내 그룹웨어 사용자의 요청 의도를 분류하는 라우터입니다. " +
                "사용자의 입력을 분석하여 다음 중 하나의 카테고리로 분류하세요: " +
                "OVERTIME(잔업/특근 신청), VACATION(휴가/연차 신청), POLICY(사내 규정 문의), GENERAL(일반 대화)."
        ).build();

        // TODO: 실제 환경에서는 DB나 YAML에서 읽어와 Registry를 초기화합니다.
        initSOPRegistry();
    }

    // Spring AI가 구조화된 출력을 위해 사용할 내부 레코드
    record IntentClassification(String category, double confidence) {}

    // 1차 고속 라우터: 키워드 기반 분류
    private AgentWorkflowSOP fastMatch(String text) {
        // 공백 제거 및 소문자 변환으로 검색 확률을 높임
        String cleanText = text.replaceAll("\\s+", "").toLowerCase();

        if (cleanText.contains("ot") || cleanText.contains("잔업") || cleanText.contains("특근") || cleanText.contains("야근") || cleanText.contains("초과근무")) {
            return sopRegistry.get("OVERTIME");
        }
        if (cleanText.contains("휴가") || cleanText.contains("연차") || cleanText.contains("반차") ||  cleanText.contains("반반차") || cleanText.contains("보상휴가") || cleanText.contains("결근")) {
            return sopRegistry.get("VACATION");
        }
        if (cleanText.contains("규정") || cleanText.contains("사규") || cleanText.contains("지침") || cleanText.contains("매뉴얼")) {
            return sopRegistry.get("POLICY");
        }

        return null;
    }
    // 메인 분류 메서드
    public AgentWorkflowSOP classify(String userMessage) {

        // 1단계: 고속 라우터가 먼저 처리 (비용 0, 속도 즉시)
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
            log.error("LLM 라우터 실패, 기본값 반환: {}", e.getMessage());
            return sopRegistry.get("GENERAL");
        }
    }

    private void initSOPRegistry() {
        sopRegistry.put("OVERTIME", new AgentWorkflowSOP(
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
                - STEP 3을 수행한 직후에는 반드시 [STOP] 하고 사용자의 대답을 기다려야 합니다.
                - 사용자의 명시적인 '승인' 응답이 존재하기 전까지는 절대로 STEP 4(잔업_상신_MCP)를 선제적으로 호출하지 마십시오.
                - MCP 도구의 `target_user_id` 파라미터에는 시스템에 제공된 {userId}를 사용하십시오.
                </constraint>
           """,
            false
        ));
        sopRegistry.put("POLICY", new AgentWorkflowSOP(
            "POLICY",
            "[사내 규정 답변]\n검색된 RAG 문서를 바탕으로 친절하고 정확하게 답변하세요.",
            true // RAG 필요!
        ));
        sopRegistry.put("GENERAL", new AgentWorkflowSOP(
            "GENERAL",
            "특별한 도구 호출 없이 친절하게 응대하세요.",
            false
        ));
    }
}