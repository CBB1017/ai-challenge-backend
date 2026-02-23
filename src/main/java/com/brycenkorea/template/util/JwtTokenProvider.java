package com.brycenkorea.template.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String secretKey; // 최소 32byte

    public String createToken(Long userId, String name, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);
        return Jwts.builder()
                   .subject(name)
                   .claim("userId", userId)
                   .claim("role", role)
                   .issuedAt(now)
                   .expiration(expiry)
                   .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                   .compact();
    }

    // name 추출
    public String getUsername(String token) {
        return Jwts.parser()
                   .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                   .build()
                   .parseSignedClaims(token)
                   .getPayload()
                   .getSubject();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String username = getUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = Jwts.parser()
                              .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                              .build()
                              .parseSignedClaims(token)
                              .getPayload()
                              .getExpiration();
        return expiration.before(new Date());
    }

    public String extractJwt(HttpServletRequest request) {
        return request.getHeader("Authorization").substring(7);
    }

}
