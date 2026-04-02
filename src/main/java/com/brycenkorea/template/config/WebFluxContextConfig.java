package com.brycenkorea.template.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Configuration
public class WebFluxContextConfig {

    @PostConstruct
    public void init() {
        // Reactor의 비동기 스레드 경계를 넘나들 때 SecurityContext를 자동으로 복사해 줌
        Hooks.enableAutomaticContextPropagation();
    }
}