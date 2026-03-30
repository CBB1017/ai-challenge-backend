package com.brycenkorea.template.service;

import com.brycenkorea.template.entity.User;
import com.brycenkorea.template.repository.UserRepository;
import com.brycenkorea.template.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public Mono<ResponseEntity<Map<String, String>>> processUserSession(String token, String nameAndPosition, String department) {
        // 1. 그룹웨어 토큰 파싱
        Claims claims = jwtTokenProvider.parseGroupwareToken(token);
        String email = claims.get("email", String.class);
        String corpCode = claims.get("corpcode", String.class); // 소문자 주의
        Date expiry = claims.getExpiration();

        String[] parts = nameAndPosition.split(" ");
        String name = parts[0];
        String position = parts.length > 1 ? parts[1] : "";

        // 2. DB 확인 및 자동 가입/업데이트 (JIT)
        return userRepository.findByEmail(email)
                             .flatMap(existingUser -> {
                                 // 기존 유저 정보 업데이트
                                 existingUser.setName(name);
                                 existingUser.setDepartment(department);
                                 existingUser.setLastLoginAt(OffsetDateTime.now());
                                 return userRepository.save(existingUser);
                             })
                             .switchIfEmpty(Mono.defer(() -> userRepository.save(User.builder()
                                                                                     .email(email)
                                                                                     .name(name)
                                                                                     .position(position)
                                                                                     .department(department)
                                                                                     .corpCode(corpCode)
                                                                                     .role("ROLE_USER")
                                                                                     .build())))
                             .map(user -> {
                                 // 3. 우리 서비스 전용 JWT 생성 (기타 정보 포함)
                                 // 토큰에 email, dept, name 등을 넣어두면 나중에 MCP가 파싱하기 편함
                                 String myToken = jwtTokenProvider.createToken(
                                     user.getEmail(),
                                     user.getRole(),
                                     user.getDepartment(),
                                     user.getName(),
                                     expiry
                                 );

                                 ResponseCookie cookie = ResponseCookie.from("accessToken", myToken)
                                                                       .maxAge(Duration.between(
                                                                           Instant.now(),
                                                                           expiry.toInstant()
                                                                       ))
                                                                       .httpOnly(true)
                                                                       .path("/")
                                                                       .build();

                                 return ResponseEntity.ok()
                                                      .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                                      .body(Map.of(
                                                          "token",
                                                          myToken,
                                                          "name",
                                                          user.getName(),
                                                          "position",
                                                          user.getPosition(),
                                                          "department",
                                                          user.getDepartment()
                                                      ));
                             });
    }
}
