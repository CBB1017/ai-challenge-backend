package com.brycenkorea.template.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import org.springframework.ai.chat.messages.Message;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final ReactiveStringRedisTemplate redisTemplate; // 💡 Reactive로 교체
    private final JsonMapper jsonMapper;
    private static final String PREFIX = "chat:memory:v2:";

    public record MessageDto(String type, String content) {}

    @Override
    public @NonNull List<Message> findByConversationId(@NonNull String conversationId) {
        return Objects.requireNonNull(redisTemplate.opsForValue()
                                                   .get(PREFIX + conversationId)
                                                   .map(this::deserializeMessages)
                                                   .subscribeOn(Schedulers.boundedElastic())
                                                   .block(Duration.ofSeconds(1)));
    }

    @Override
    public void saveAll(@NonNull String conversationId, @NonNull List<Message> messages) {
        Mono.fromCallable(() -> serializeMessages(messages))
            .flatMap(json -> redisTemplate.opsForValue().set(PREFIX + conversationId, json, Duration.ofDays(7)))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(); // 💡 비동기로 저장 실행
    }

    private String serializeMessages(List<Message> messages) {
        try {
            List<MessageDto> dtos = messages.stream()
                                            .map(m -> new MessageDto(m.getMessageType().getValue(), m.getText()))
                                            .toList();
            return jsonMapper.writeValueAsString(dtos);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    private List<Message> deserializeMessages(String json) {
        try {
            List<MessageDto> dtos = jsonMapper.readValue(json, new TypeReference<>() {});
            return dtos.stream().map(dto -> {
                String type = dto.type() != null ? dto.type().toLowerCase() : "user";
                return switch (type) {
                    case "assistant" -> new AssistantMessage(dto.content());
                    case "system" -> new SystemMessage(dto.content());
                    default -> new UserMessage(dto.content());
                };
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Deserialization failed", e);
            return List.of();
        }
    }

    @Override
    public @NonNull List<String> findConversationIds() { return List.of(); } // 필요시 구현
    @Override
    public void deleteByConversationId(@NonNull String id) { redisTemplate.delete(PREFIX + id).subscribe(); }
}