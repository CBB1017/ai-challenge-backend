package com.brycenkorea.template.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest; // WebFlux용으로 변경
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

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

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String username = getUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = Jwts.parser()
                              .verifyWith(key)
                              .build()
                              .parseSignedClaims(token)
                              .getPayload()
                              .getExpiration();
        return expiration.before(new Date());
    }

    /**
     * 우리 서비스의 SecretKey로 서명된 토큰을 검증하고 페이로드를 반환합니다.
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                   .verifyWith(key) // 생성자에서 만든 SecretKey로 검증
                   .build()
                   .parseSignedClaims(token)
                   .getPayload();
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
}