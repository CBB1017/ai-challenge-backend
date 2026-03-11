package com.brycenkorea.template.tools;

import com.brycenkorea.template.dto.request.CrawlerRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;

@Component
public class PythonCrawlerTools {

    private final RestClient restClient;

    // RestClient.Builder를 주입받아 파이썬 서버 주소 세팅 (도커 환경이면 URL 변경 필요)
    public PythonCrawlerTools(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://localhost:8081").build();
    }

    // 이 어노테이션과 설명이 핵심입니다! Gemini가 이걸 읽고 호출을 결정합니다.
    @Tool(description = "사내 시스템에서 근태(attendance), 팀원 정보(member), 회의실 예약 현황(meeting-room)을 조회합니다.")
    public String callPythonCrawler(CrawlerRequest request) {
        try {
            // FastAPI 호출: POST /crawling/{action}
            return restClient.post()
                             .uri("/crawling/{action}", request.action())
                             .body(request)
                             .retrieve()
                             .body(String.class); // JSON 문자열 그대로 AI에게 전달
        } catch (Exception e) {
            return "크롤링 에러 발생: " + e.getMessage();
        }
    }
}