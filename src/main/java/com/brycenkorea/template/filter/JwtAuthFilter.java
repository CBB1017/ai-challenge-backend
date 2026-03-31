package com.brycenkorea.template.filter;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.api.CommonResponse;
import com.brycenkorea.template.exception.ApiException;
import com.brycenkorea.template.security.CustomUserDetailsService;
import com.brycenkorea.template.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.brycenkorea.template.config.DynamicSecurityConfig.WHITELIST;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    @Autowired
    JsonMapper jsonMapper;
    // 1. 문자열 배열을 PathPattern 리스트로 미리 파싱 (매번 파싱하면 성능 저하)
    private static final List<PathPattern> WHITELIST_PATTERNS = Arrays.stream(WHITELIST)
                                                                      .map(pattern -> new PathPatternParser().parse(
                                                                          pattern))
                                                                      .toList();

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.info("path={}", exchange.getRequest().getURI());
        log.info("cookies={}", exchange.getRequest().getCookies());
        log.info("headers={}", exchange.getRequest().getHeaders());
        if (isWhitelisted(path)) return chain.filter(exchange);

        // 1. 헤더에서 먼저 찾기
        String token = extractTokenFromHeader(exchange);

        // 2. 헤더에 없으면 쿠키에서 찾기
        if (token == null) {
            token = exchange.getRequest().getCookies().getFirst("accessToken") != null
                ? Objects.requireNonNull(exchange.getRequest().getCookies().getFirst("accessToken")).getValue()
                : null;
        }

        if (token == null) {
            return sendError(exchange, HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHORIZED, "Token missing");
        }

        try {
            // 1. 동기적인 JWT 파싱 (이 안에서 터지면 아래 onErrorResume으로 감)
            String finalToken = token;
            return Mono.just(token)
                       .map(jwtTokenProvider::getUsername)
                       .flatMap(username -> {
                           log.info("username={}", username);
                           // 2. Reactive하게 사용자 조회
                           return userDetailsService.findByUsername(username);
                       })
                       .flatMap(userDetails -> {
                           // 3. 토큰 검증 및 보안 컨텍스트 설정
                           if (jwtTokenProvider.validateToken(finalToken, userDetails)) {
                               UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                   userDetails, null, userDetails.getAuthorities()
                               );
                               return chain.filter(exchange)
                                           .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                           }
                           return sendError(exchange, HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHORIZED, "Invalid JWT token");
                       })
                       .onErrorResume(e -> {
                           log.error("Security Filter Error: ", e); // 여기서 SQL 에러가 찍힙니다.
                           if (e instanceof ApiException) { // 커스텀 예외 처리
                               return sendError(exchange, HttpStatus.UNAUTHORIZED, ApiResultCode.INVALID_TOKEN, e.getMessage());
                           }
                           return sendError(exchange, HttpStatus.BAD_REQUEST, ApiResultCode.INVALID_TOKEN, "Authentication failed");
                       });
        } catch (Exception e) {
            return sendError(exchange, HttpStatus.BAD_REQUEST, ApiResultCode.INVALID_TOKEN, "JWT parsing failed");
        }
    }

    private static @NonNull SecurityContextImpl getSecurityContext(UserDetails userDetails) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );
        // SecurityContext를 Reactive용으로 저장하고 다음 필터로 진행
        return new SecurityContextImpl(auth);
    }

    private boolean isWhitelisted(String uri) {
        // 2. 요청 URI를 PathContainer로 변환
        PathContainer pathContainer = PathContainer.parsePath(uri);

        // 3. 미리 파싱된 패턴들과 매칭 확인
        return WHITELIST_PATTERNS.stream()
                                 .anyMatch(pattern -> pattern.matches(pathContainer));
    }

    private Mono<Void> sendError(ServerWebExchange exchange, HttpStatus status, ApiResultCode code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
//        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        CommonResponse<Void> resBody = CommonResponse.error(code, message);
        byte[] bytes = jsonMapper.writeValueAsString(resBody).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private String extractTokenFromHeader(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}