// src/test/java/com/brycenkorea/template/util/TestJwtUtil.java
package com.brycenkorea.template.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class TestJwtUtil {
    @Value("${jwt.secret}")
    private String jwtSecret;

    public String createTestToken(Long userId, String name, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        return Jwts.builder()
                   .subject(name)
                   .claim("role", role)
                   .claim("userId", userId)
                   .claim("email", name + "@bry.co.kr")
                   .issuedAt(now)
                   .expiration(expiry)
                   .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                   .compact();
    }
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                            .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
        return claims.get("userId", Long.class);
    }
}