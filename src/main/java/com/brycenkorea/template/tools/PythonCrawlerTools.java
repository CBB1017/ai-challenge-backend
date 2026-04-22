package com.brycenkorea.template.tools;

import com.brycenkorea.template.dto.request.CrawlerRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 파이썬 크롤러와의 통신을 담당하는 도구
 * 혹시라도 LLM이 어떠한 액션을 하길 원한다면 여기서 직접 하려면 해당 코드를 샘플로 추가 수정 후 등록
 */
@Component
public class PythonCrawlerTools {

    private final WebClient webClient;

    public PythonCrawlerTools(WebClient pythonCrawlerWebClient) {
        this.webClient = pythonCrawlerWebClient;
    }

    @Tool(description = "사내 시스템에서 근태(attendance), 팀원 정보(member), 회의실 예약 현황(meeting-room)을 조회합니다.")
    public String callPythonCrawler(CrawlerRequest request) {
        try {
            // WebClient를 이용한 비동기 호출
            return webClient.post()
                .uri("/crawling/{action}", request.action())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (Exception e) {
            return "크롤링 에러 발생: " + e.getMessage();
        }
    }
}