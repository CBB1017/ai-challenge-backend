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

    /**
     * 💡 그룹웨어 JWT에서 페이로드(Claims)를 추출하는 메서드
     * 서명 키(SecretKey)를 모를 경우를 대비하여 검증 없이 파싱하는 로직입니다.
     */
    public Claims parseGroupwareToken(String token) {
        try {
            // 서명 검증(verifyWith) 없이 페이로드만 읽어옵니다.
            // 프론트엔드에서 이미 인증된 결과물로 넘겨주는 것이므로 믿고 파싱합니다.
            return Jwts.parser()
                       .build()
                       .parseUnsecuredClaims(token.substring(0, token.lastIndexOf('.') + 1))
                       .getPayload();
        } catch (Exception e) {
            // 만약 그룹웨어 토큰이 표준 JWT라면 아래 방식으로 시도
            String[] chunks = token.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(chunks[1]));
            // Jackson 등을 이용해 Map이나 Claims로 변환하는 로직 필요
            throw new RuntimeException("그룹웨어 토큰 파싱 실패", e);
        }
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
    /**
     * 💡 우리 서비스의 SecretKey로 서명된 토큰을 검증하고 페이로드를 반환합니다.
     * 이 메서드 하나로 서명 검증 + 만료 체크 + 데이터 추출을 한 번에 끝냅니다.
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