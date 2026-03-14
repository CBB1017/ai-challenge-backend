package com.brycenkorea.template.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * r2dbc 자동 구성을 우회하기 위함
 */
@Configuration
public class JdbcConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    public DataSource dataSource() {
        // DataSourceBuilder의 .url() 메서드를 사용하면
        // 내부적으로 HikariCP가 요구하는 jdbcUrl 문제까지 깔끔하게 자체 해결합니다.
        return DataSourceBuilder.create()
                                .driverClassName(driverClassName)
                                .url(url)
                                .username(username)
                                .password(password)
                                .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}