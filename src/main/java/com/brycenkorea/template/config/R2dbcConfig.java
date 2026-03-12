//package com.brycenkorea.template.config;
//
//import com.brycenkorea.template.security.CustomUserDetails;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.domain.ReactiveAuditorAware;
//import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.ReactiveSecurityContextHolder;
//import reactor.core.publisher.Mono;
//
//@Configuration
//@EnableR2dbcAuditing
//public class R2dbcConfig {
//
//    @Bean
//    public ReactiveAuditorAware<Long> auditorAware() {
//        return () -> ReactiveSecurityContextHolder.getContext().flatMap(context -> {
//            // 1. Authentication 객체 추출 및 null 체크
//            Authentication authentication = context.getAuthentication();
//            if (authentication == null || !authentication.isAuthenticated()) {
//                return Mono.empty();
//            }
//
//            // 2. Principal 객체 타입 체크 및 ID 추출
//            Object principal = authentication.getPrincipal();
//            if (principal instanceof CustomUserDetails user) {
//                return Mono.just(user.getUserId());
//            }
//
//            return Mono.empty();
//        });
//    }
//}