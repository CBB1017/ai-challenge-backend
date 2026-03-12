package com.brycenkorea.template.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import org.springframework.ai.chat.messages.Message;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "chat:memory:v2:";
    // 내부용 심플 DTO (인터페이스 대신 구체적인 레코드로 직렬화/역직렬화)
    public record MessageDto(String type, String content) {}

    @Autowired
    JsonMapper jsonMapper;

    @Override
    public @NonNull List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        return keys == null ? List.of() : keys.stream().map(k -> k.replace(PREFIX, "")).toList();
    }

    @Override
    public @NonNull List<Message> findByConversationId(@NonNull String conversationId) {
        String json = redisTemplate.opsForValue().get(PREFIX + conversationId);
        if (json == null) return List.of();
        try {
            // 1. JSON -> DTO 리스트로 안전하게 변환
            List<MessageDto> dtos = jsonMapper.readValue(json, new TypeReference<>() {});

            // 2. DTO -> Spring AI Message 구현체로 복원
            return dtos.stream().map(dto -> {
                // 💡 변경 포인트 2: Null 방어 로직 추가
                String type = dto.type() != null ? dto.type().toLowerCase() : "user";
                String content = dto.content() != null ? dto.content() : "";

                return switch (type) {
                    case "assistant" -> new AssistantMessage(content);
                    case "system" -> new SystemMessage(content);
                    default -> new UserMessage(content);
                };
            }).map(m -> (Message) m).toList();
        } catch (Exception e) {
            log.info("역직렬화 실패로 데이터 제거");
            redisTemplate.delete(PREFIX + conversationId);
            return List.of();
        }
    }

    @Override
    public void saveAll(@NonNull String conversationId, @NonNull List<Message> messages) {
        try {
            // 1. Message 구현체 -> 심플 DTO로 변환
            List<MessageDto> dtos = messages.stream()
                                            .map(m -> new MessageDto(m.getMessageType().getValue(), m.getText()))
                                            .toList();

            // 2. DTO 리스트를 JSON으로 저장
            String json = jsonMapper.writeValueAsString(dtos);

            // 데이터 무한 증식을 막기 위해 7일(TTL) 보관 설정
            redisTemplate.opsForValue().set(PREFIX + conversationId, json, Duration.ofDays(7));
        } catch (Exception e) {
            throw new RuntimeException("Redis 직렬화 실패", e);
        }
    }

    @Override
    public void deleteByConversationId(@NonNull String conversationId) {
        redisTemplate.delete(PREFIX + conversationId);
    }
}