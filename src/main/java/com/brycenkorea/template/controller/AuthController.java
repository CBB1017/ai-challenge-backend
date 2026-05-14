package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.request.LoginRequest;
import com.brycenkorea.template.dto.response.LoginResponse;
import com.brycenkorea.template.service.BirthdayService;
import com.brycenkorea.template.service.BoardService;
import com.brycenkorea.template.service.LoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final WebClient pythonCrawlerWebClient;
    private final LoginService loginService;
    private final BoardService boardService;
    private final BirthdayService birthdayService;

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> performLogin(@RequestBody LoginRequest loginRequest) {
        return pythonCrawlerWebClient.post()
            .uri("/api/login")
            .bodyValue(loginRequest)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new RuntimeException("클라이언트 에러: " + body)))
            )
            .bodyToMono(LoginResponse.class)
            .flatMap(res -> {
                if (!"success".equals(res.status())) {
                    return Mono.error(new BadCredentialsException(res.message()));
                }
                return loginService.processUserSession(res.user(), res.cookies());
            })
            .flatMap(authResult -> {
                // 비동기 작업 실행 (응답 대기 안 함)
                Mono.fromRunnable(() -> {
                    boardService.fetchAndSaveAllBoardData(loginRequest.getUserId(), true)
                        .subscribe(null, e -> log.error("Board Error", e));
                    birthdayService.fetchAndSaveBirthdays(loginRequest.getUserId(), true)
                        .subscribe(null, e -> log.error("Birthday Error", e));
                }).subscribeOn(Schedulers.boundedElastic()).subscribe();

                return Mono.just(authResult);
            });
    }

    @GetMapping("/check")
    public Mono<ResponseEntity<Map<String, String>>> checkAuth(Authentication auth) {
        String userId = Objects.requireNonNull(auth.getPrincipal()).toString();

        return loginService.checkUserSession(userId);
    }
}