package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.request.LoginRequest;
import com.brycenkorea.template.security.CustomUserDetails;
import com.brycenkorea.template.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    // 반환 타입을 실제 데이터 구조인 Map<String, String>으로 명시합니다.
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest loginRequest) {
        log.info("login request: {}", loginRequest);
        log.info("pass => {}", passwordEncoder.encode(loginRequest.getPassword()));
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
            loginRequest.getUsername(),
            loginRequest.getPassword()
        );

        return authenticationManager.authenticate(token).map(authentication -> {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = Objects.requireNonNull(userDetails).getUserId();

            String jwt = jwtTokenProvider.createToken(userId, authentication.getName(), "USER");
            // 쿠키 생성
            ResponseCookie cookie = ResponseCookie.from("accessToken", jwt)
                                                  .httpOnly(true)    // 자바스크립트 접근 차단 (보안)
                                                  .secure(false)     // 로컬 테스트(http)라면 false, 배포(https)라면 true
                                                  .path("/")
                                                  .maxAge(3600)      // 1시간
                                                  .sameSite("Lax")   // 크로스 도메인 설정에 따라 None 또는 Lax
                                                  .build();

            return ResponseEntity.ok()
                                 .header(HttpHeaders.SET_COOKIE, cookie.toString()) // 💡 헤더에 추가!
                                 .body(Collections.singletonMap("token", jwt));        });
    }
}