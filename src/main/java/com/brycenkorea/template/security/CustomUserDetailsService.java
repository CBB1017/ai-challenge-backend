package com.brycenkorea.template.security;

import com.brycenkorea.template.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements ReactiveUserDetailsService { // 인터페이스 변경

    private final MemberRepository memberRepository;

    @Override
    public @NonNull Mono<UserDetails> findByUsername(@NonNull String email) { // 메서드명과 반환 타입 변경
        return memberRepository.findByEmail(email)
                               .switchIfEmpty(Mono.error(new UsernameNotFoundException("User not found: " + email)))
                               .map(member -> new CustomUserDetails(
                                   member.getId(),
                                   member.getEmail(),
                                   member.getPassword(),
                                   List.of(new SimpleGrantedAuthority("ROLE_USER"))
                               ));
    }
}