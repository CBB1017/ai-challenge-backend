package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.response.BoardPostResponse;
import com.brycenkorea.template.dto.response.CrawlerBoardResponse;
import com.brycenkorea.template.dto.response.CrawlerRecentBoardResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BoardService {
    private static final String BOARD_POSTS_KEY = "board:posts";
    private static final String RECENT_POSTS_KEY = "board:recent";
    private final ChatClient statelessChatClient;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final SseBroadcaster sseBroadcaster;
    private final JsonMapper jsonMapper;

    public BoardService(
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
     * Python 크롤러를 호출하여 개인화된 게시판 포스트 정보를 가져와 Redis에 저장합니다.
     *
     * @param userId       사용자 ID
     * @param shouldNotify SSE 알림 전송 여부
     */
    public Mono<Void> fetchAndSaveBoardPosts(String userId, boolean shouldNotify) {
        String redisKey = BOARD_POSTS_KEY + ":" + userId;

        return Mono.fromCallable(() -> {
                log.info("MCP 도구를 통한 게시판 데이터 fetch 시작 (User: {})", userId);
                return statelessChatClient.prompt()
                    .system("You are a data retrieval agent. Your ONLY job is to call the 'get_board_posts' tool and return its result as a raw JSON string. DO NOT add any other text or explanation. The result should start with { and end with }.")
                    .user("게시판 포스트 목록을 가져와줘.")
                    .toolContext(Map.of("userId", userId))
                    .call()
                    .content();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(content -> {
                if (content.isBlank()) {
                    log.error("MCP tool returned empty content for board posts");
                    return Mono.empty();
                }

                try {
                    // LLM이 마크다운 코드로 감쌌을 경우를 대비해 정제
                    String json = content.replaceAll("(?s)```(?:json)?\\n?(.*?)\\n?```", "$1").trim();
                    if (!json.startsWith("{")) {
                        int start = json.indexOf("{");
                        int end = json.lastIndexOf("}");
                        if (start != -1 && end != -1 && end > start) {
                            json = json.substring(start, end + 1);
                        }
                    }

                    CrawlerBoardResponse res = jsonMapper.readValue(json, CrawlerBoardResponse.class);
                    if (res != null && "success".equals(res.status())) {
                        return reactiveRedisTemplate.opsForValue().set(redisKey, res.data())
                            .doOnSuccess(v -> {
                                if (shouldNotify) {
                                    sseBroadcaster.sendEvent(userId, "board-update", res.data());
                                }
                            })
                            .then();
                    } else {
                        log.error("Failed to fetch board posts: {}", res != null ? res.message() : "Unknown error");
                    }
                } catch (Exception e) {
                    log.error("게시판 데이터 파싱 중 오류 발생. Content: {}", content, e);
                }
                return Mono.empty();
            })
            .doOnSuccess(v -> log.info("Successfully fetched and saved board posts to Redis for user: {}", userId))
            .doOnError(e -> log.error("Error fetching board posts via MCP", e));
    }

    /**
     * MCP 도구를 호출하여 개인화된 최근 게시물 정보를 가져와 Redis에 저장합니다.
     *
     * @param userId       사용자 ID
     * @param shouldNotify SSE 알림 전송 여부
     */
    public Mono<Void> fetchAndSaveRecentPosts(String userId, boolean shouldNotify) {
        String redisKey = RECENT_POSTS_KEY + ":" + userId;

        return Mono.fromCallable(() -> {
                log.info("MCP 도구를 통한 최근 게시물 데이터 fetch 시작 (User: {})", userId);
                return statelessChatClient.prompt()
                    .system("You are a data retrieval agent. Your ONLY job is to call the 'get_recent_posts' tool and return its result as a raw JSON string. DO NOT add any other text or explanation. The result should start with { and end with }.")
                    .user("최근 게시물 목록을 가져와줘.")
                    .toolContext(Map.of("userId", userId))
                    .call()
                    .content();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(content -> {
                if (content.isBlank()) {
                    log.error("MCP tool returned empty content for recent posts");
                    return Mono.empty();
                }

                try {
                    // LLM이 마크다운 코드로 감쌌을 경우를 대비해 정제
                    String json = content.replaceAll("(?s)```(?:json)?\\n?(.*?)\\n?```", "$1").trim();
                    if (!json.startsWith("{")) {
                        int start = json.indexOf("{");
                        int end = json.lastIndexOf("}");
                        if (start != -1 && end != -1 && end > start) {
                            json = json.substring(start, end + 1);
                        }
                    }

                    CrawlerRecentBoardResponse res = jsonMapper.readValue(json, CrawlerRecentBoardResponse.class);
                    if (res != null && "success".equals(res.status())) {
                        return reactiveRedisTemplate.opsForValue().set(redisKey, res.data())
                            .doOnSuccess(v -> {
                                if (shouldNotify) {
                                    sseBroadcaster.sendEvent(userId, "recent-board-update", res.data());
                                }
                            })
                            .then();
                    } else {
                        log.error("Failed to fetch recent board posts: {}", res != null ? res.message() : "Unknown error");
                    }
                } catch (Exception e) {
                    log.error("최근 게시물 데이터 파싱 중 오류 발생. Content: {}", content, e);
                }
                return Mono.empty();
            })
            .doOnSuccess(v -> log.info("Successfully fetched and saved recent board posts to Redis for user: {}", userId))
            .doOnError(e -> log.error("Error fetching recent board posts via MCP", e));
    }

    /**
     * 모든 게시판 관련 데이터를 비동기로 호출하여 저장합니다.
     */
    public Mono<Void> fetchAndSaveAllBoardData(String userId, boolean shouldNotify) {
        return Mono.when(
            fetchAndSaveBoardPosts(userId, shouldNotify),
            fetchAndSaveRecentPosts(userId, shouldNotify)
        ).doOnSuccess(v -> log.info("All board data fetched and saved successfully for user: {}", userId));
    }

    /**
     * Redis에서 개인화된 게시판 포스트 정보를 가져옵니다.
     */
    public Mono<Map<String, List<BoardPostResponse>>> getBoardPosts(String userId) {
        String redisKey = BOARD_POSTS_KEY + ":" + userId;

        return reactiveRedisTemplate.opsForValue().get(redisKey)
            .map(obj -> {
                try {
                    Map<String, List<BoardPostResponse>> result = jsonMapper.convertValue(obj, new TypeReference<>() {
                    });
                    return result != null ? result : Collections.<String, List<BoardPostResponse>>emptyMap();
                } catch (Exception e) {
                    log.error("게시판 데이터 변환 중 오류 발생", e);
                    return Collections.<String, List<BoardPostResponse>>emptyMap();
                }
            })
            .defaultIfEmpty(Collections.emptyMap());
    }

    /**
     * Redis에서 개인화된 최근 게시물 정보를 가져옵니다.
     */
    public Mono<List<BoardPostResponse>> getRecentPosts(String userId) {
        String effectiveUserId = userId != null ? userId : "system";
        String redisKey = RECENT_POSTS_KEY + ":" + effectiveUserId;

        return reactiveRedisTemplate.opsForValue().get(redisKey)
            .map(obj -> {
                try {
                    List<BoardPostResponse> result = jsonMapper.convertValue(obj, new TypeReference<>() {
                    });
                    return result != null ? result : Collections.<BoardPostResponse>emptyList();
                } catch (Exception e) {
                    log.error("최근 게시물 데이터 변환 중 오류 발생", e);
                    return Collections.<BoardPostResponse>emptyList();
                }
            })
            .defaultIfEmpty(Collections.emptyList());
    }

    /**
     * 모든 게시판 관련 데이터를 가져와서 하나의 맵으로 합쳐서 반환합니다.
     */
    public Mono<Map<String, List<BoardPostResponse>>> getBoardAndRecentPosts(String userId) {
        return Mono.zip(getBoardPosts(userId), getRecentPosts(userId))
            .map(tuple -> {
                Map<String, List<BoardPostResponse>> combined = new HashMap<>(tuple.getT1());
                combined.put("recent", tuple.getT2());
                return combined;
            });
    }
}