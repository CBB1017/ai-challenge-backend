package com.brycenkorea.template;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TemplateApplicationTests {
    @MockitoBean
    AuthenticationManager authenticationManager;
	@Test
	void contextLoads() {
	}

}
