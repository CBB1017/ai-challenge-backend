package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.request.LoginRequest;
import com.brycenkorea.template.dto.response.LoginResponse;
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
//                                     .contentType(MediaType.APPLICATION_JSON) // 컨텐트 타입 명시
//                                     .accept(MediaType.APPLICATION_JSON)      // 응답 타입 명시
                                     .bodyValue(loginRequest)
                                     .retrieve()
                                     .onStatus(
                                         HttpStatusCode::is4xxClientError, response ->
                                         response.bodyToMono(String.class).flatMap(body -> {
                                             log.error("Error Detail: {}", body);
                                             return Mono.error(new RuntimeException("클라이언트 에러: " + body));
                                         })
                                     )
                                     .bodyToMono(LoginResponse.class) // 응답을 Map으로 받거나 전용 DTO 사용
                                     .flatMap(res -> {
                                         if (!"success".equals(res.status())) {
                                             return Mono.error(new BadCredentialsException(res.message()));
                                         }

                                         return loginService.processUserSession(
                                             res.user(),
                                             res.cookies()
                                         );
                                     });
    }

    @GetMapping("/check")
    public Mono<ResponseEntity<Map<String, String>>> checkAuth(Authentication auth) {
        return loginService.checkUserSession(auth.getName());
    }
}