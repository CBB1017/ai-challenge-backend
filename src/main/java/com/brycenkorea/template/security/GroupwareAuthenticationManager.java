package com.brycenkorea.template.security;

import com.brycenkorea.template.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class GroupwareAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider; // 주입 필요

    @Override
    public @NonNull Mono<Authentication> authenticate(Authentication authentication) {
        String token = Objects.requireNonNull(authentication.getCredentials()).toString();

        try {
            // 1. 우리 서비스의 SecretKey로 서명된 '내 토큰'을 검증 및 파싱
            // (JwtTokenProvider에 구현된 검증 로직 활용)
            Claims claims = jwtTokenProvider.getClaims(token);

            // 2. 만료 시간 검증 (이미 parse 과정에서 JJWT가 해주지만 명시적 처리 가능)
            if (claims.getExpiration().before(new Date())) {
                return Mono.error(new BadCredentialsException("세션이 만료되었습니다."));
            }

            // 3. 토큰에서 정보 추출 (우리 토큰에 저장해둔 값들)
            String email = claims.getSubject(); // email
            String role = claims.get("role", String.class);
            String dept = claims.get("dept", String.class); // 부서 정보 추가
            String name = claims.get("name", String.class); // 부서 정보 추가

            // 4. 인증 토큰 반환
            return Mono.just(new GroupwareAuthenticationToken(
                email,
                dept,
                name,
                token,
                List.of(new SimpleGrantedAuthority(role))
            ));

        } catch (Exception e) {
            return Mono.error(new BadCredentialsException("유효하지 않은 토큰입니다."));
        }
    }
}