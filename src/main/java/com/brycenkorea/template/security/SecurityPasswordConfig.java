package com.brycenkorea.template.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityPasswordConfig {

    @Bean
    public PasswordEncoder sha256PasswordEncoder() {
        return new Sha256PasswordEncoder();
    }
}