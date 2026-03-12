package com.brycenkorea.template;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;

@SecurityScheme(
		name = "bearer",         // 사용할 이름
		type = SecuritySchemeType.HTTP,
		scheme = "bearer", //  "basic"
		bearerFormat = "JWT"
)
@SpringBootApplication
@EnableR2dbcAuditing
public class TemplateApplication {

	public static void main(String[] args) {
		SpringApplication.run(TemplateApplication.class, args);
	}

}
