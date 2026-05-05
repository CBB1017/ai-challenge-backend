package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.response.BoardPostResponse;
import com.brycenkorea.template.dto.response.CrawlerBoardResponse;
import com.brycenkorea.template.dto.response.CrawlerRecentBoardResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardService {
    private final WebClient pythonCrawlerWebClient;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final SseBroadcaster sseBroadcaster;
    private final JsonMapper jsonMapper;

    private static final String BOARD_POSTS_KEY = "board:posts";
    private static final String RECENT_POSTS_KEY = "board:recent";

    /**
     * Python 크롤러를 호출하여 게시판 포스트 정보를 가져와 Redis에 저장합니다.
     * @param shouldNotify SSE 알림 전송 여부
     */
    public Mono<Void> fetchAndSaveBoardPosts(boolean shouldNotify) {
        return pythonCrawlerWebClient.post()
            .uri("/crawling/board-posts")
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).flatMap(body -> {
                log.error("Python Crawler Error Response: {}", body);
                return Mono.error(new RuntimeException("크롤러 API 호출 실패: " + body));
            }))
            .bodyToMono(CrawlerBoardResponse.class)
            .flatMap(res -> {
                if ("success".equals(res.status())) {
                    return reactiveRedisTemplate.opsForValue().set(BOARD_POSTS_KEY, res.data())
                        .doOnSuccess(v -> {
                            if (shouldNotify) {
                                sseBroadcaster.broadcastEvent("board-update", res.data());
                            }
                        })
                        .then();
                } else {
                    log.error("Failed to fetch board posts: {}", res.message());
                    return Mono.empty();
                }
            })
            .doOnSuccess(v -> log.info("Successfully fetched and saved board posts to Redis"))
            .doOnError(e -> log.error("Error fetching board posts", e));
    }

    /**
     * Python 크롤러를 호출하여 최근 게시물 정보를 가져와 Redis에 저장합니다.
     */
    public Mono<Void> fetchAndSaveRecentPosts(boolean shouldNotify) {
        return pythonCrawlerWebClient.post()
            .uri("/crawling/recent-posts")
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).flatMap(body -> {
                log.error("Python Crawler Error Response: {}", body);
                return Mono.error(new RuntimeException("크롤러 API 호출 실패: " + body));
            }))
            .bodyToMono(CrawlerRecentBoardResponse.class)
            .flatMap(res -> {
                if ("success".equals(res.status())) {
                    return reactiveRedisTemplate.opsForValue().set(RECENT_POSTS_KEY, res.data())
                        .doOnSuccess(v -> {
                            if (shouldNotify) {
                                sseBroadcaster.broadcastEvent("recent-board-update", res.data());
                            }
                        })
                        .then();
                } else {
                    log.error("Failed to fetch recent board posts: {}", res.message());
                    return Mono.empty();
                }
            })
            .doOnSuccess(v -> log.info("Successfully fetched and saved recent board posts to Redis"))
            .doOnError(e -> log.error("Error fetching recent board posts", e));
    }

    /**
     * 모든 게시판 관련 데이터를 비동기로 호출하여 저장합니다.
     */
    public Mono<Void> fetchAndSaveAllBoardData(boolean shouldNotify) {
        return Mono.when(
            fetchAndSaveBoardPosts(shouldNotify),
            fetchAndSaveRecentPosts(shouldNotify)
        ).doOnSuccess(v -> log.info("All board data fetched and saved successfully"));
    }

    /**
     * 모든 게시판 관련 데이터를 비동기로 호출하여 저장합니다. (JobRunr용 Blocking 버전)
     */
    public void fetchAndSaveAllBoardDataBlocking() {
        fetchAndSaveAllBoardData(true).block();
    }

    /**
     * Redis에서 게시판 포스트 정보를 가져옵니다.
     */
    public Mono<Map<String, List<BoardPostResponse>>> getBoardPosts() {
        return reactiveRedisTemplate.opsForValue().get(BOARD_POSTS_KEY)
            .map(obj -> {
                try {
                    Map<String, List<BoardPostResponse>> result = jsonMapper.convertValue(obj, new TypeReference<>() {});
                    return result != null ? result : Collections.<String, List<BoardPostResponse>>emptyMap();
                } catch (Exception e) {
                    log.error("게시판 데이터 변환 중 오류 발생", e);
                    return Collections.<String, List<BoardPostResponse>>emptyMap();
                }
            })
            .defaultIfEmpty(Collections.emptyMap());
    }

    /**
     * Redis에서 최근 게시물 정보를 가져옵니다.
     */
    public Mono<List<BoardPostResponse>> getRecentPosts() {
        return reactiveRedisTemplate.opsForValue().get(RECENT_POSTS_KEY)
            .map(obj -> {
                try {
                    List<BoardPostResponse> result = jsonMapper.convertValue(obj, new TypeReference<>() {});
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
    public Mono<Map<String, List<BoardPostResponse>>> getBoardAndRecentPosts() {
        return Mono.zip(getBoardPosts(), getRecentPosts())
            .map(tuple -> {
                Map<String, List<BoardPostResponse>> combined = new HashMap<>(tuple.getT1());
                combined.put("recent", tuple.getT2());
                return combined;
            });
    }

    /**
     * Python 크롤러를 호출하여 게시판 포스트 정보를 가져와 Redis에 저장합니다. (JobRunr용 Blocking 버전)
     * @deprecated Use fetchAndSaveAllBoardDataBlocking instead
     */
    @Deprecated
    public void fetchAndSaveBoardPostsBlocking() {
        fetchAndSaveBoardPosts(true).block();
    }
}
