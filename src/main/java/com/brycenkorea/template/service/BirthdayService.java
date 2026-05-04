package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.response.CrawlerBirthdayResponse;
import com.brycenkorea.template.dto.response.BirthdayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirthdayService {
    private final WebClient pythonCrawlerWebClient;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    private static final String BIRTHDAY_KEY = "birthday:list";

    /**
     * Python 크롤러를 호출하여 생일자 정보를 가져와 Redis에 저장합니다.
     */
    public Mono<Void> fetchAndSaveBirthdays() {
        return pythonCrawlerWebClient.post()
            .uri("/crawling/birthday")
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).flatMap(body -> {
                log.error("Python Crawler Error Response: {}", body);
                return Mono.error(new RuntimeException("크롤러 API 호출 실패: " + body));
            }))
            .bodyToMono(CrawlerBirthdayResponse.class)
            .flatMap(res -> {
                if ("success".equals(res.status())) {
                    return reactiveRedisTemplate.opsForValue().set(BIRTHDAY_KEY, res.data()).then();
                } else {
                    log.error("Failed to fetch birthdays: {}", res.message());
                    return Mono.empty();
                }
            })
            .doOnSuccess(v -> log.info("Successfully fetched and saved birthdays to Redis"))
            .doOnError(e -> log.error("Error fetching birthdays", e));
    }

    /**
     * Python 크롤러를 호출하여 생일자 정보를 가져와 Redis에 저장합니다. (JobRunr용 Blocking 버전)
     */
    public void fetchAndSaveBirthdaysBlocking() {
        fetchAndSaveBirthdays().block();
    }

    /**
     * Redis에서 생일자 정보를 가져옵니다.
     */
    @SuppressWarnings("unchecked")
    public Mono<List<BirthdayResponse>> getBirthdays() {
        return reactiveRedisTemplate.opsForValue().get(BIRTHDAY_KEY)
            .map(obj -> (List<BirthdayResponse>) obj)
            .defaultIfEmpty(Collections.emptyList());
    }
}
