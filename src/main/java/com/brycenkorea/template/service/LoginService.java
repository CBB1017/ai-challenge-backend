package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.response.LoginResponse;
import com.brycenkorea.template.entity.User;
import com.brycenkorea.template.repository.UserRepository;
import com.brycenkorea.template.util.JwtTokenProvider;
import io.netty.handler.codec.http.cookie.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public Mono<ResponseEntity<Map<String, String>>> processUserSession(LoginResponse.CrawlerUser userData, List<Map<String, Object>> cookies) {
        String email = userData.userId(); // bc.mun@~ 또는 도메인 제거된 bc.mun으로 들어옴
        log.info("userData={}", userData);
        log.info("cookies={}", cookies);

        Date expiry = null;

        for (Map<String, Object> cookie : cookies) {
            // 1. 쿠키의 이름이 "UserID"인지 확인
            if ("UserID".equals(cookie.get("name"))) {
                Object expiresObj = cookie.get("expires");

                if (expiresObj instanceof Number expiresNum) {
                    // 2. 전달된 expires는 초(seconds) 단위의 Timestamp (1.775...E9)
                    long timestampMillis = (long) (expiresNum.doubleValue() * 1000);
                    expiry = new Date(timestampMillis);
                    log.info("쿠키 만료일 파싱 성공: {}", expiry);
                }
                break;
            }
        }

        if(expiry == null) throw new IllegalArgumentException("Cookie max-age not found");

        String[] parts = userData.nameAndPosition().split(" ");
        String name = parts[0];
        String position = parts.length > 1 ? parts[1] : "";

        // 2. DB 확인 및 자동 가입/업데이트 (JIT)
        Date finalExpiry = expiry;
        return userRepository.findByEmail(email)
                             .flatMap(existingUser -> {
                                 // 기존 유저 정보 업데이트
                                 existingUser.setName(name);
                                 existingUser.setDepartment(userData.dept());
                                 existingUser.setLastLoginAt(OffsetDateTime.now());
                                 return userRepository.save(existingUser);
                             })
                             .switchIfEmpty(Mono.defer(() -> userRepository.save(User.builder()
                                                                                     .email(email)
                                                                                     .name(name)
                                                                                     .position(position)
                                                                                     .department(userData.dept())
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
                                     finalExpiry
                                 );

                                 ResponseCookie cookie = ResponseCookie.from("accessToken", myToken)
                                                                       .maxAge(Duration.between(
                                                                           Instant.now(),
                                                                           finalExpiry.toInstant()
                                                                       ))
                                                                       .httpOnly(true)
                                                                       .path("/")
                                                                       .build();

                                 return ResponseEntity.ok()
                                                      .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                                      .body(Map.of(
                                                          "token", myToken,
                                                          "email", user.getEmail(),
                                                          "name", user.getName(),
                                                          "position", user.getPosition(),
                                                          "department", user.getDepartment()
                                                      ));
                             });
    }
    public Mono<ResponseEntity<Map<String, String>>> checkUserSession(String email) {
        // 1. DB 확인 및 자동 가입/업데이트 (JIT)
        return userRepository.findByEmail(email)
                             .mapNotNull(user -> ResponseEntity.ok()
                                                    .body(Map.of(
                                                      "email", user.getEmail(),
                                                      "name", user.getName(),
                                                      "position", user.getPosition(),
                                                      "department", user.getDepartment()
                                                  )));
    }
}
