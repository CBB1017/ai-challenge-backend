package com.brycenkorea.template.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class GroupwareAuthenticationToken extends AbstractAuthenticationToken {
    private final String principal; // email 또는 username
    @Getter
    private final String department;
    @Getter
    private final String name;
    private final String token;

    public GroupwareAuthenticationToken(String principal, String department, String name, String token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.department = department;
        this.name = name;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return token; }
    @Override
    public Object getPrincipal() { return principal; }

}