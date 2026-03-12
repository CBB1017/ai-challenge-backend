package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.request.LoginRequest;
import com.brycenkorea.template.security.CustomUserDetails;
import com.brycenkorea.template.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    // 반환 타입을 실제 데이터 구조인 Map<String, String>으로 명시합니다.
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
            loginRequest.getName(),
            loginRequest.getPassword()
        );

        return authenticationManager.authenticate(token)
                                    .map(authentication -> {
                                        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
                                        Long userId = userDetails.getUserId();

                                        String jwt = jwtTokenProvider.createToken(userId, authentication.getName(), "USER");
                                        // 명확하게 Map 타입을 반환
                                        return ResponseEntity.ok(Collections.singletonMap("token", jwt));
                                    });
    }
}