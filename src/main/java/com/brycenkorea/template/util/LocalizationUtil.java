package com.brycenkorea.template.util;

import java.util.Locale;
import java.util.Map;

public class LocalizationUtil {

    private static final Map<String, String> NEW_CHAT_TITLE = Map.of(
        "ko", "새로운 대화",
        "en", "New Conversation",
        "ja", "新しい対話",
        "vi", "Cuộc trò chuyện mới"
    );

    public static String getNewChatTitle(String lang) {
        return NEW_CHAT_TITLE.getOrDefault(lang != null ? lang.toLowerCase() : "ko", "새로운 대화");
    }

    public static Locale getLocale(String lang) {
        if (lang == null) return Locale.KOREAN;
        return switch (lang.toLowerCase()) {
            case "en" -> Locale.ENGLISH;
            case "ja" -> Locale.JAPANESE;
            case "vi" -> Locale.of("vi");
            default -> Locale.KOREAN;
        };
    }

    public static String getErrorMessage(String lang, String type) {
        boolean isEn = "en".equalsIgnoreCase(lang);
        boolean isJa = "ja".equalsIgnoreCase(lang);
        boolean isVi = "vi".equalsIgnoreCase(lang);

        if ("quota".equals(type)) {
            if (isEn) return "API quota exceeded. Please try again in about 1 minute.";
            if (isJa) return "APIの割り当てを超過しました。約1分後に再度お試しください。";
            if (isVi) return "Vượt quá định mức API. Vui lòng thử lại sau khoảng 1 phút.";
            return "현재 API 호출 할당량이 초과되었습니다. 잠시 후(약 1분 뒤) 다시 요청해 주시기 바랍니다.";
        }
        if ("safety".equals(type)) {
            if (isEn) return "Your input was blocked by safety policies. Please try a different approach.";
            if (isJa) return "入力内容が安全ポリシーによりブロックされました。別の方法で質問してください。";
            if (isVi) return "Nội dung nhập đã bị chặn bởi chính sách an toàn. Vui lòng thử cách khác.";
            return "입력하신 내용이 안전 정책에 의해 차단되었습니다. 다른 방식으로 질문해 주세요.";
        }
        
        if (isEn) return "Sorry, the service is currently busy. Please try again later.";
        if (isJa) return "申し訳ありません。現在サービスが混み合っています。後でもう一度お試しください。";
        if (isVi) return "Xin lỗi, dịch vụ hiện đang bận. Vui lòng thử lại sau.";
        return "죄송합니다. 현재 서비스 이용량이 많아 잠시 후 다시 시도해 주세요.";
    }
}
