package com.brycenkorea.template.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

//@Component
@Slf4j
public class LoggingFilter implements WebFilter {
    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        log.info("[START] {} {}", method, path);

        return chain.filter(exchange).doFinally(signalType -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null ? exchange.getResponse()
                .getStatusCode()
                .value() : 0;

            log.info("[END] Path: {}, Status: {}, Duration: {}ms", path, statusCode, duration);
        });
    }
}