package com.brycenkorea.template.dto.response;

import java.util.List;
import java.util.Map;

/**
 * FastAPI 크롤러 서버로부터 받는 로그인 응답 전체 구조
 */
public record LoginResponse(String status,         // "success" 또는 "fail"
                            String message,        // "Login successful" 등
                            List<Map<String, Object>> cookies, // 쿠키 리스트 (도메인, 경로, 값 포함)
                            CrawlerUser user       // 유저 상세 정보
) {
    /**
     * 유저 상세 정보 중첩 레코드
     */
    public record CrawlerUser(String nameAndPosition,   // "문병찬 대리"
                              String dept,        // "DX 2Team"
                              String userId        // "bc.mun"
    ) {}
}