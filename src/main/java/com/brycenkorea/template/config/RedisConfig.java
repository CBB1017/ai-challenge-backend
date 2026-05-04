package com.brycenkorea.template.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfig {
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(new JsonMapper());
        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder =
            RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        RedisSerializationContext<String, Object> context = builder.value(serializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}