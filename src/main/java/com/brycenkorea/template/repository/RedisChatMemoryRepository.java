package com.brycenkorea.template.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final ReactiveStringRedisTemplate redisTemplate; // 💡 Reactive로 교체
    private final JsonMapper jsonMapper;
    private static final String PREFIX = "chat:memory:v2:";

    record MessageDto(String type,
                      String content,
                      Map<String, Object> metadata,
                      List<AssistantMessage.ToolCall> toolCalls) {}

    @Override
    public @NonNull List<Message> findByConversationId(@NonNull String conversationId) {
        log.info("findByConversationId");
        log.info("conversationId: {}", conversationId);
        //        return Objects.requireNonNull(redisTemplate.opsForValue()
        //                                                   .get(PREFIX + conversationId)
        //                                                   .map(this::deserializeMessages)
        //                                                   .subscribeOn(Schedulers.boundedElastic())
        //                                                   .block(Duration.ofSeconds(1)));
        String json = redisTemplate.opsForValue().get(PREFIX + conversationId).block(Duration.ofSeconds(1));

        if (json == null) {
            return List.of();
        }

        return deserializeMessages(json);
    }

    @Override
    public void saveAll(@NonNull String conversationId, @NonNull List<Message> messages) {
        log.info("saveAll");
        log.info("conversationId: {}", conversationId);
        log.info("messages: {}", messages);

        //        Mono.fromCallable(() -> serializeMessages(messages))
        //            .flatMap(json -> redisTemplate.opsForValue().set(PREFIX + conversationId, json, Duration.ofDays(7)))
        //            .subscribeOn(Schedulers.boundedElastic())
        //            .subscribe(); // 💡 비동기로 저장 실행
        String json = serializeMessages(messages);

        redisTemplate.opsForValue().set(PREFIX + conversationId, json, Duration.ofDays(7)).block();
    }

    private String serializeMessages(List<Message> messages) {
        try {
            List<MessageDto> dtos = messages.stream().map(m -> {
                if (m instanceof AssistantMessage assistant) {
                    return new MessageDto(
                        m.getMessageType().getValue(),
                        m.getText(),
                        m.getMetadata(),
                        assistant.getToolCalls()
                    );
                }

                return new MessageDto(m.getMessageType().getValue(), m.getText(), m.getMetadata(), List.of());
            }).toList();
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

                Map<String, Object> metadata = dto.metadata() != null ? dto.metadata() : Map.of();

                return switch (type) {
                    case "assistant" -> AssistantMessage.builder()
                                                        .content(dto.content())
                                                        .properties(metadata)
                                                        .toolCalls(dto.toolCalls() != null
                                                            ? dto.toolCalls()
                                                            : List.of())
                                                        .build();

                    case "system" -> SystemMessage.builder().text(dto.content()).metadata(metadata).build();

                    default -> UserMessage.builder().text(dto.content()).metadata(metadata).build();
                };
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Deserialization failed", e);
            return List.of();
        }
    }

    @Override
    public @NonNull List<String> findConversationIds() {
        return List.of();
    }

    @Override
    public void deleteByConversationId(@NonNull String id) {
        redisTemplate.delete(PREFIX + id).block();
    }
}