package com.brycenkorea.template.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;

    // 생성자에서 키를 한 번만 생성
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(String email, String role, String dept, String name, Date expiry) {
        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .claim("dept", dept)
            .claim("name", name)
            .expiration(expiry)
            .signWith(key)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("잘못된 JWT 서명 또는 구조입니다.", e);
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰입니다.", e);
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 형식의 JWT 토큰입니다.", e);
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 비어있거나 잘못되었습니다.", e);
        }
        return false;
    }

    /**
     * 서비스의 SecretKey로 서명된 토큰을 검증하고 페이로드를 반환합니다.
     */
    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key) // 생성자에서 만든 SecretKey로 검증
            .build().parseSignedClaims(token).getPayload();
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String getDepartment(String token) {
        return getClaims(token).get("dept", String.class);
    }

    public String getName(String token) {
        return getClaims(token).get("name", String.class);
    }

    // 💡 JwtAuthFilter에서 사용할 권한(Authorities) 추출 메서드 추가
    public List<GrantedAuthority> getAuthorities(String token) {
        String role = getClaims(token).get("role", String.class);
        if (role != null && !role.isBlank()) {
            return List.of(new SimpleGrantedAuthority(role));
        }
        return Collections.emptyList();
    }
}