package com.brycenkorea.template.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OptimizedChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final JsonMapper jsonMapper;

    // 최대 응답 길이 (Fallback 용도)
    private final int maxToolResponseLength = 1500;
    // 배열 데이터 최대 허용 개수 (JSON 파싱 성공 시)
    private final int maxArrayItems = 5;

    public OptimizedChatMemory(ChatMemory delegate, JsonMapper jsonMapper) {
        this.delegate = delegate;
        this.jsonMapper = jsonMapper;
    }
    @Override
    public void add(String conversationId, Message message) {
        delegate.add(conversationId, optimizeMessage(message));
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> optimized = new ArrayList<>();
        for (Message msg : messages) {
            optimized.add(optimizeMessage(msg));
        }
        delegate.add(conversationId, optimized);
    }

    @Override
    public List<Message> get(String conversationId) {
        return delegate.get(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }

    private Message optimizeMessage(Message msg) {
        if (msg instanceof ToolResponseMessage toolMsg) {
            List<ToolResponseMessage.ToolResponse> newResponses = new ArrayList<>();
            for (ToolResponseMessage.ToolResponse tr : toolMsg.getResponses()) {
                String data = tr.responseData();

                // 1. JSON 파싱 시도하여 구조적으로 줄이기
                data = pruneJsonData(data);

                newResponses.add(new ToolResponseMessage.ToolResponse(tr.id(), tr.name(), data));
            }
            return ToolResponseMessage.builder()
                    .responses(newResponses)
                    .build();
        } else if (msg instanceof AssistantMessage assistantMsg) {
            // Spring AI 2.0.0-M6 issue: AssistantMessage containing only tool calls has null content.
            if (assistantMsg.getText() == null) {
                if (!assistantMsg.getToolCalls().isEmpty()) {
                    return AssistantMessage.builder()
                            .content("")
                            .toolCalls(assistantMsg.getToolCalls())
                            .build();
                }
                return AssistantMessage.builder()
                        .content("")
                        .build();
            }
        }
        return msg;
    }

    /**
     * JSON 문자열을 파싱하여 배열의 크기를 줄임 (JSON 형식을 깨지 않음)
     */
    private String pruneJsonData(String data) {
        if (data == null || data.isBlank()) return data;

        try {
            JsonNode rootNode = jsonMapper.readTree(data);

            if (rootNode.isArray()) {
                pruneArray((ArrayNode) rootNode);
                return jsonMapper.writeValueAsString(rootNode);
            } else if (rootNode.isObject()) {
                pruneObject((ObjectNode) rootNode);
                return jsonMapper.writeValueAsString(rootNode);
            }

        } catch (JacksonException e) {
            // JSON이 아닌 일반 텍스트인 경우 기존 방식(Truncation) 사용
            if (data.length() > maxToolResponseLength) {
                log.debug("Non-JSON tool response truncated (length: {})", data.length());
                return data.substring(0, maxToolResponseLength) + "...[TRUNCATED FOR DB OPTIMIZATION]";
            }
        }
        return data;
    }

    private void pruneObject(ObjectNode objectNode) {
        objectNode.properties().forEach(entry -> {
            JsonNode child = entry.getValue();
            if (child.isArray()) {
                pruneArray((ArrayNode) child);
            } else if (child.isObject()) {
                pruneObject((ObjectNode) child);
            }
        });
    }

    private void pruneArray(ArrayNode arrayNode) {
        // 1. 배열 크기 먼저 줄이기
        if (arrayNode.size() > maxArrayItems) {
            int originalSize = arrayNode.size();
            for (int i = arrayNode.size() - 1; i >= maxArrayItems; i--) {
                arrayNode.remove(i);
            }
            ObjectNode metaNode = jsonMapper.createObjectNode();
            metaNode.put("_meta_info", String.format("... %d more items truncated for context optimization", originalSize - maxArrayItems));
            arrayNode.add(metaNode);
        }

        // 2. 살아남은 요소들에 대해 재귀 탐색 진행 (누락되었던 부분)
        arrayNode.forEach(child -> {
            if (child.isArray()) {
                pruneArray((ArrayNode) child);
            } else if (child.isObject()) {
                pruneObject((ObjectNode) child);
            }
        });
    }
}
