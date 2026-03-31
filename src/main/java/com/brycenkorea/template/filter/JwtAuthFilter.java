package com.brycenkorea.template.filter;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.api.CommonResponse;
import com.brycenkorea.template.exception.ApiException;
import com.brycenkorea.template.security.CustomUserDetailsService;
import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import com.brycenkorea.template.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
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

    // 문자열 배열을 PathPattern 리스트로 미리 파싱 (매번 파싱하면 성능 저하)
    private static final List<PathPattern> WHITELIST_PATTERNS = Arrays.stream(WHITELIST)
                                                                      .map(pattern -> new PathPatternParser().parse(
                                                                          pattern))
                                                                      .toList();
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    @Autowired
    JsonMapper jsonMapper;

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.info("path={}", exchange.getRequest().getURI());
        log.info("cookies={}", exchange.getRequest().getCookies());
        log.info("headers={}", exchange.getRequest().getHeaders());
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

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
            // 1. 토큰 유효성 검증 (만료, 서명 오류 등 발생 시 catch 블록으로 이동)
            if (!jwtTokenProvider.validateToken(token)) {
                return sendError(exchange, HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHORIZED, "Invalid JWT token");
            }

            // 2. DB 조회 없이 토큰에서 직접 정보 추출 (Stateless)
            String username = jwtTokenProvider.getUsername(token);
            String department = jwtTokenProvider.getDepartment(token);
            String name = jwtTokenProvider.getName(token);
            List<GrantedAuthority> authorities = jwtTokenProvider.getAuthorities(token);

            // 3. 커스텀 인증 객체 생성
            GroupwareAuthenticationToken auth = new GroupwareAuthenticationToken(
                username,
                department,
                name,
                token,
                authorities
            );

            // 4. 리액티브 컨텍스트에 저장 후 다음 필터 체인 진행
            return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));

        } catch (Exception e) {
            log.error("Security Filter Error: ", e);
            if (e instanceof ApiException) {
                return sendError(exchange, HttpStatus.UNAUTHORIZED, ApiResultCode.INVALID_TOKEN, e.getMessage());
            }
            // JWT 파싱 에러 (ExpiredJwtException 등) 처리
            return sendError(exchange, HttpStatus.BAD_REQUEST, ApiResultCode.INVALID_TOKEN, "Authentication failed");
        }
    }

    private boolean isWhitelisted(String uri) {
        // 2. 요청 URI를 PathContainer로 변환
        PathContainer pathContainer = PathContainer.parsePath(uri);

        // 3. 미리 파싱된 패턴들과 매칭 확인
        return WHITELIST_PATTERNS.stream().anyMatch(pattern -> pattern.matches(pathContainer));
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