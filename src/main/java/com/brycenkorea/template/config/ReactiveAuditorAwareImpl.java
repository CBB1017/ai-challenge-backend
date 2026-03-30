package com.brycenkorea.template.config;

import com.brycenkorea.template.security.CustomUserDetails;
import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
public class ReactiveAuditorAwareImpl implements ReactiveAuditorAware<String> { // email이 PK이므로 String

    @Override
    public @NonNull Mono<String> getCurrentAuditor() {
        return ReactiveSecurityContextHolder.getContext()
                                            .mapNotNull(SecurityContext::getAuthentication)
                                            .filter(Authentication::isAuthenticated)
                                            .cast(GroupwareAuthenticationToken.class)
                                            .map(GroupwareAuthenticationToken::getName); // principal(email) 반환
    }
}