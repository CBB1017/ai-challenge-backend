package com.brycenkorea.template.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient pythonCrawlerWebClient() {
        return WebClient.builder()
                        .baseUrl("http://localhost:8081")
                        // 필요하다면 타임아웃, 헤더, 로깅 등을 여기서 한 번에 공통 설정할 수 있습니다.
                        .build();
    }
}