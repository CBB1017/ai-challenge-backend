package com.brycenkorea.template.tools;

import com.brycenkorea.template.dto.request.CrawlerRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MCP가 아닌 Spring에서 Tool을 생성하여 LLM에서 호출시키는 방식.
 * MCP로 구현할 경우 이런 과정이 필요 없음.
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
                .bodyValue(request) // body() 대신 bodyValue() 사용
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (Exception e) {
            return "크롤링 에러 발생: " + e.getMessage();
        }
    }
}