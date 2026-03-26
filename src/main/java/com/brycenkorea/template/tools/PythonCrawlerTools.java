package com.brycenkorea.template.tools;

import com.brycenkorea.template.dto.request.CrawlerRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;

/**
 * MCP가 아닌 Spring에서 Tool을 생성하여 LLM에서 호출시키는 방식.
 * MCP로 구현할 경우 이런 과정이 필요 없음.
 * 혹시라도 LLM이 어떠한 액션을 하길 원한다면 여기서 직접 하려면 해당 코드를 샘플로 추가 수정 후 등록
 */
@Component
public class PythonCrawlerTools {

    private final RestClient restClient;

    public PythonCrawlerTools(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://localhost:8081").build();
    }

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