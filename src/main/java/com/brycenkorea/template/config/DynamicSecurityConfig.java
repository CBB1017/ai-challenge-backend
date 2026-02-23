package com.brycenkorea.template.config;

import com.brycenkorea.template.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class DynamicSecurityConfig {

    public static final List<String> WHITELIST = List.of(
        "/swagger-ui/**",
        "/swagger-ui/index.html",
        "/v3/api-docs/**",
        "/swagger-resources/**",
        "/webjars/**",
        "/api/auth/login",
        "/api/crawling/**"
        // 기타 허용할 경로들
    );
    private final AuthModeProperties authModeProperties;
    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final CorsFilter corsFilter;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String mode = authModeProperties.getMode();

        // 공통: API 허용 설정
        http.authorizeHttpRequests(auth -> auth.requestMatchers(WHITELIST.toArray(String[]::new))
                                               .permitAll()
                                               .anyRequest()
                                               .authenticated());
        http.addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class);

        if ("jwt".equalsIgnoreCase(mode)) {
            // JWT 인증 방식만 활성
            http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                    jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        } else if ("basic".equalsIgnoreCase(mode)) {
            // Basic Auth만 활성 (필터 생성 요망)
            http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(withDefaults())
                .formLogin(AbstractHttpConfigurer::disable);
        } else { // "form" (default)
            http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable);
        }

        http.userDetailsService(userDetailsService);
        return http.build();
    }
}
