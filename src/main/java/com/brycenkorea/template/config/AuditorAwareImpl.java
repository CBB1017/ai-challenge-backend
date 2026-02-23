package com.brycenkorea.template.config;

import com.brycenkorea.template.security.CustomUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<Long> {
    @Override
    @NonNull
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        // 커스텀 UserDetails(Member PK가 Long인 경우)라면 아래처럼
        if (principal instanceof CustomUserDetails userDetails) {
            return Optional.of(userDetails.getUserId()); // getId()는 userId 리턴
        }

        return Optional.empty();
    }
}