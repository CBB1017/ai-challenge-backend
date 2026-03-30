package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.request.LoginRequest;
import com.brycenkorea.template.dto.response.LoginResponse;
import com.brycenkorea.template.service.LoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final WebClient pythonCrawlerWebClient;
    private final LoginService loginService;

    @PostMapping("/login")
    public Mono<ResponseEntity<?>> performLogin(@RequestBody LoginRequest loginRequest) {
        return pythonCrawlerWebClient.post()
                                     .uri("/api/login")
                                     .contentType(MediaType.APPLICATION_JSON) // 컨텐트 타입 명시
                                     .accept(MediaType.APPLICATION_JSON)      // 응답 타입 명시
                                     .bodyValue(loginRequest)
                                     .retrieve()
                                     .onStatus(
                                         HttpStatusCode::is4xxClientError, response ->
                                         response.bodyToMono(String.class).flatMap(body -> {
                                             // 여기서 422 에러 메시지의 상세 내용을 로그로 찍을 수 있습니다.
                                             log.error("422 Error Detail: {}", body);
                                             return Mono.error(new RuntimeException("클라이언트 에러: " + body));
                                         })
                                     )
                                     .bodyToMono(LoginResponse.class) // 응답을 Map으로 받거나 전용 DTO 사용
                                     .flatMap(res -> {
                                         if (!"success".equals(res.status())) {
                                             return Mono.error(new BadCredentialsException("로그인 실패"));
                                         }

                                         LoginResponse.CrawlerUser userData = res.user();
                                         String nameAndPos = userData.nameAndPosition();
                                         String dept = userData.dept();

                                         return loginService.processUserSession(
                                             res.token(),
                                             nameAndPos,
                                             dept
                                         );
                                     });
    }

    @GetMapping("/check")
    public Mono<ResponseEntity<Map<String, String>>> checkAuth(org.springframework.security.core.Authentication auth) {
        // 프론트엔드에서 쓸 수 있게 로그인된 유저 ID를 넘겨줌
        Map<String, String> userInfo = Map.of("username", auth.getName());

        return Mono.just(ResponseEntity.ok(userInfo));
    }
}