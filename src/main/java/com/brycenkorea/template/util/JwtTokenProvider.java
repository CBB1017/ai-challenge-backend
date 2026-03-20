package com.brycenkorea.template.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest; // WebFlux용으로 변경
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;

    // 생성자에서 키를 한 번만 생성
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Long userId, String email, String role) {
        Date now = new Date();
        // 1시간
        long validityInMilliseconds = 3600_000;
        Date expiry = new Date(now.getTime() + validityInMilliseconds);
        return Jwts.builder()
                   .subject(email)
                   .claim("userId", userId)
                   .claim("role", role)
                   .issuedAt(now)
                   .expiration(expiry)
                   .signWith(key)
                   .compact();
    }

    public String getUsername(String token) {
        return Jwts.parser()
                   .verifyWith(key)
                   .build()
                   .parseSignedClaims(token)
                   .getPayload()
                   .getSubject();
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

    // WebFlux용 추출 메서드
    public String extractJwt(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}