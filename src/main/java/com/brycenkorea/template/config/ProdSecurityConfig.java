package com.brycenkorea.template.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;

@Configuration
@Profile("!dev")
@EnableWebFluxSecurity
public class ProdSecurityConfig {

    @Bean
    public SecurityWebFilterChain prodSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(csrf -> csrf.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
            .authorizeExchange(exchanges -> exchanges.pathMatchers("/api/member/**", "/v3/api-docs/**")
                .permitAll()
                .anyExchange()
                .authenticated())
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .build();
    }
}