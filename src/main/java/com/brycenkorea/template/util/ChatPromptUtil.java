package com.brycenkorea.template.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public class ChatPromptUtil {

    /**
     * 현재 일시를 yyyy-MM-dd HH:mm:ss 형식으로 반환합니다.
     */
    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 현재 요일을 해당 언어에 맞게 반환합니다.
     */
    public static String getCurrentDayOfWeek(String language) {
        Locale locale = LocalizationUtil.getLocale(language);
        return LocalDateTime.now().getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
    }

    /**
     * 첫 대화 내용을 바탕으로 채팅방 제목 요약을 위한 프롬프트를 생성합니다.
     */
    public static String getTitleSummaryPrompt(String language, String userPrompt, String aiResponse) {
        return switch (language != null ? language.toLowerCase() : "ko") {
            case "en" -> String.format(
                "Please summarize the following conversation as a chat room title within 15 characters. Output only the summarized string without any other explanation.\nUser: %s\nAI: %s",
                userPrompt, aiResponse
            );
            case "ja" -> String.format(
                "次の会話を元に、チャットルームのタイトルを15文字以内で要約してください。解説などは入れず、要約した文字列のみを出力してください。\nユーザー: %s\nAI: %s",
                userPrompt, aiResponse
            );
            case "vi" -> String.format(
                "Dựa trên cuộc trò chuyện sau, hãy tóm tắt tiêu đề phòng trò chuyện trong vòng 15 ký tự. Chỉ cung cấp chuỗi tóm tắt mà không có bất kỳ giải thích nào khác.\nNgười dùng: %s\nAI: %s",
                userPrompt, aiResponse
            );
            default -> String.format(
                "다음 대화를 바탕으로 채팅방의 제목을 15자 이내로 요약해줘. 그리고 다른 미사여구는 필요없이 요약한 문자열만 전달해줘. \n유저: %s\nAI: %s",
                userPrompt, aiResponse
            );
        };
    }

    /**
     * 시스템 프롬프트(SOP 룰 + 언어 설정)를 구성합니다.
     */
    public static String buildSystemPrompt(String rules, String language) {
        String lang = (language != null) ? language : "ko";
        return rules + "\n\n" + "Please respond in the user's language (" + lang + ").";
    }

    /**
     * 이메일 요약 비동기 작업 접수 안내 메시지를 반환합니다.
     */
    public static String getEmailSummaryInfoMsg(String language) {
        boolean isKo = "ko".equalsIgnoreCase(language) || (language != null && language.startsWith("ko"));
        if (isKo) {
            return "📥 **이메일 요약 요청이 접수되었습니다.**\n내용이 많을 경우 최대 10개까지만 요약되며 시간이 다소 소요될 수 있습니다. 완료 시 알림으로 알려드릴게요!";
        } else {
            return "📥 **Email summary request received.**\nIt may take some time if there's a lot of content. We'll notify you when it's complete!";
        }
    }

    /**
     * 의도 분류를 위한 인텐트 라우터의 시스템 프롬프트를 반환합니다.
     */
    public static String getIntentRouterSystemPrompt() {
        return """
                You are a high-performance router that classifies the intent of internal groupware users.

                [Mission]
                Analyze the user's current input and previous conversation context to classify them into one of the following categories:
                1. OVERTIME_ONEDAY: Applying for overtime/holiday work on a specific date.
                2. OVERTIME_MONTHLY: Batch application for overtime/holiday work for a specific month.
                3. VACATION: Applying for leave, annual leave, half-day leave, or compensatory leave.
                4. EMAIL_SUMMARY: Inquiry or summary of received emails.
                5. POLICY: Inquiry about internal regulations, guidelines, manuals, rules, or standards (Requires RAG).
                6. GENERAL: General conversation, or **simple inquiry/confirmation of personal information (attendance, vacation balance, commute records)** that does not yet involve a formal application/approval process.

                [Important Constraints]
                - If the user explicitly mentions "apply", "submit", or "request" for overtime or vacation, use categories 1, 2, or 3.
                - If the user simply asks to "check", "show", or "tell" their own status (e.g., "What time did I check in?", "How much leave do I have?"), classify as 'GENERAL'.
                - For example, if 'overtime hours were calculated' or 'vacation dates were checked' just before and the AI asked 'Shall I submit it?', you must accurately classify it as 'OVERTIME_ONEDAY' or 'VACATION' at the moment of user's consent.
                - Output ONLY the category name.
                """;
    }
}
