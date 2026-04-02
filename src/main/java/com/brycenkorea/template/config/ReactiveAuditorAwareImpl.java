package com.brycenkorea.template.config;

import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveAuditorAwareImpl implements ReactiveAuditorAware<String> { // email이 PK이므로 String

    @Override
    public @NonNull Mono<String> getCurrentAuditor() {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .cast(GroupwareAuthenticationToken.class)
            .mapNotNull(GroupwareAuthenticationToken::getPrincipal); // principal(email) 반환
    }
}