package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.response.CrawlerBirthdayResponse;
import com.brycenkorea.template.dto.response.BirthdayResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BirthdayService {
    private final ChatClient statelessChatClient;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final SseBroadcaster sseBroadcaster;
    private final JsonMapper jsonMapper;

    private static final String BIRTHDAY_KEY = "birthday:list";

    public BirthdayService(
        @Qualifier("statelessChatClient") ChatClient statelessChatClient,
        ReactiveRedisTemplate<String, Object> reactiveRedisTemplate,
        SseBroadcaster sseBroadcaster,
        JsonMapper jsonMapper
    ) {
        this.statelessChatClient = statelessChatClient;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.sseBroadcaster = sseBroadcaster;
        this.jsonMapper = jsonMapper;
    }

    /**
     * MCP 도구를 호출하여 생일자 정보를 가져와 Redis에 저장합니다.
     * @param userId 요청한 사용자 ID (null이면 system)
     * @param shouldNotify SSE 알림 전송 여부
     */
    public Mono<Void> fetchAndSaveBirthdays(String userId, boolean shouldNotify) {

        if(userId == null) return Mono.empty();
        return Mono.fromCallable(() -> {
            log.info("MCP 도구를 통한 생일자 데이터 fetch 시작 (User: {})", userId);
            return statelessChatClient.prompt()
                .system("You are a data retrieval agent. Your ONLY job is to call the 'get_birthdays' tool and return its result as a raw JSON string. DO NOT add any other text or explanation. The result should start with { and end with }.")
                .user("이번 달 사내 생일자 목록을 가져와줘.")
                .toolContext(Map.of("userId", userId))
                .call()
                .content();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(content -> {
            if (content.isBlank()) {
                log.error("MCP tool returned empty content for birthdays");
                return Mono.empty();
            }

            try {
                // LLM이 마크다운 코드로 감쌌을 경우를 대비해 정제
                String json = content.replaceAll("(?s)```(?:json)?\\n?(.*?)\\n?```", "$1").trim();
                if (!json.startsWith("{")) {
                    // 가끔 정규식이 실패할 경우를 대비한 추가 처리
                    int start = json.indexOf("{");
                    int end = json.lastIndexOf("}");
                    if (start != -1 && end != -1 && end > start) {
                        json = json.substring(start, end + 1);
                    }
                }

                CrawlerBirthdayResponse res = jsonMapper.readValue(json, CrawlerBirthdayResponse.class);
                if (res != null && res.data() != null) {
                    return reactiveRedisTemplate.opsForValue().set(BIRTHDAY_KEY, res.data())
                        .doOnSuccess(v -> {
                            if (shouldNotify) {
                                sseBroadcaster.broadcastEvent("birthday-update", res.data());
                            }
                        })
                        .then();
                } else {
                    log.error("Invalid birthday data format: {}", json);
                }
            } catch (Exception e) {
                log.error("생일자 데이터 파싱 중 오류 발생. Content: {}", content, e);
            }
            return Mono.empty();
        })
        .doOnSuccess(v -> log.info("Successfully fetched and saved birthdays to Redis"))
        .doOnError(e -> log.error("Error fetching birthdays via MCP", e));
    }

    /**
     * MCP 도구를 호출하여 생일자 정보를 가져와 Redis에 저장합니다. (JobRunr용 Blocking 버전)
     */
    public void fetchAndSaveBirthdaysBlocking() {
        fetchAndSaveBirthdays(null, true).block();
    }

    /**
     * Redis에서 생일자 정보를 가져옵니다.
     */
    public Mono<List<BirthdayResponse>> getBirthdays() {
        return reactiveRedisTemplate.opsForValue().get(BIRTHDAY_KEY)
            .map(obj -> {
                try {
                    // obj가 List인 경우 각 요소를 확실하게 변환하도록 처리
                    if (obj instanceof List<?> list) {
                        return list.stream()
                            .map(item -> jsonMapper.convertValue(item, BirthdayResponse.class))
                            .toList();
                    }
                    return jsonMapper.convertValue(obj, new TypeReference<List<BirthdayResponse>>() {});
                } catch (Exception e) {
                    log.error("생일자 데이터 변환 중 오류 발생", e);
                    return Collections.<BirthdayResponse>emptyList();
                }
            })
            .defaultIfEmpty(Collections.emptyList());
    }
}
