package com.brycenkorea.template.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CustomHeaderFilter implements WebFilter { // WebFilter 구현

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. 헤더 읽기 예시
        //        String customHeader = exchange.getRequest().getHeaders().getFirst("X-Custom-Header");
        //        log.info("Custom Header received: {}", customHeader);

        // 2. 특정 조건에 따른 로직 수행 가능
        // 만약 헤더 검증 실패 시 에러를 던지고 싶다면:
        // if (customHeader == null) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        // 3. 다음 필터로 진행 (비동기 체이닝)
        return chain.filter(exchange);
    }
}