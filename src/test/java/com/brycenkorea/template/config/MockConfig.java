package com.brycenkorea.template.config;

import com.brycenkorea.template.service.MemberService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class MockConfig {
    @Bean
    public MemberService userService() {
        return mock(MemberService.class); // org.mockito.Mockito.mock()
    }
}