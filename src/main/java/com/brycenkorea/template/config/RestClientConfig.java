package com.brycenkorea.template.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // Spring이 RestClient.Builder를 주입할 수 있도록 빈으로 등록합니다.
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}