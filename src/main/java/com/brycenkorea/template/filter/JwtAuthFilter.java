package com.brycenkorea.template.filter;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.api.CommonResponse;
import com.brycenkorea.template.security.CustomUserDetailsService;
import com.brycenkorea.template.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.brycenkorea.template.config.DynamicSecurityConfig.WHITELIST;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    ) throws ServletException, IOException
    {
        if (isWhitelisted(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        response.setContentType("application/json;charset=UTF-8");

        if (!hasValidAuthHeader(request)) {
            sendError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                ApiResultCode.UNAUTHORIZED,
                "Authorization header is missing or invalid"
            );
            return;
        }

        String jwt = jwtTokenProvider.extractJwt(request);
        String username = parseJwtUsername(jwt, response);
        if (username == null) {
            return; // 이미 에러 응답을 내려서 종료됨
        }

        if (!authenticate(jwt, username, request, response)) {
            return; // 실패 시 응답 내려서 종료
        }

        chain.doFilter(request, response);
    }

    private boolean isWhitelisted(String uri) {
        return WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private boolean hasValidAuthHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    private boolean authenticate(
        String jwt,
        String username,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException
    {
        if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            return true; // 이미 인증된 사용자이거나 username 없음(앞에서 이미 에러 리턴)
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (jwtTokenProvider.validateToken(jwt, userDetails)) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            return true;
        } else {
            sendError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                ApiResultCode.UNAUTHORIZED,
                "JWT token is invalid or expired"
            );
            return false;
        }
    }

    public String parseJwtUsername(String jwt, HttpServletResponse response) throws IOException {
        try {
            return jwtTokenProvider.getUsername(jwt);
        } catch (Exception e) {
            sendError(
                response,
                HttpServletResponse.SC_BAD_REQUEST,
                ApiResultCode.INVALID_TOKEN,
                "JWT token parsing failed: " + e.getMessage()
            );
            return null;
        }
    }
    private void sendError(
        HttpServletResponse response,
        int httpStatus,
        ApiResultCode code,
        String message
    ) throws IOException {
        log.warn("[JWT-AUTH][status:{}][code:{}] {}", httpStatus, code, message);
        response.setStatus(httpStatus);
        CommonResponse<Void> resBody = CommonResponse.error(code, message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(resBody));
    }
}
