package com.brycenkorea.template.config;

import com.brycenkorea.template.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
@EnableWebFluxSecurity // WebFlux 보안 활성화
public class DynamicSecurityConfig {
    public static final String[] WHITELIST = {
        "/swagger-ui/**", "/v3/api-docs/**", "/api/auth/login", "/api/crawling/**"
    };

    private final AuthModeProperties authModeProperties;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        String mode = authModeProperties.getMode();

        http.cors(withDefaults());
        // CSRF 비활성화 (Stateless API 기준)
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        // 권한 설정
        http.authorizeExchange(exchanges -> exchanges.pathMatchers(org.springframework.http.HttpMethod.OPTIONS)
                                                     .permitAll()
                                                     .pathMatchers(WHITELIST)
                                                     .permitAll()
                                                     .anyExchange()
                                                     .authenticated());

        if ("jwt".equalsIgnoreCase(mode)) {
            http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance()) // Stateless
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        } else if ("basic".equalsIgnoreCase(mode)) {
            http.httpBasic(withDefaults());
        } else {
            http.formLogin(withDefaults());
        }

        return http.build();
    }
}