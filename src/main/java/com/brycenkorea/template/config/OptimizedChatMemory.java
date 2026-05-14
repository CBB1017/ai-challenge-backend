package com.brycenkorea.template.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;

public class OptimizedChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final int maxToolResponseLength = 1000;

    public OptimizedChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
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
                if (data.length() > maxToolResponseLength) {
                    data = data.substring(0, maxToolResponseLength) + "...[TRUNCATED FOR DB OPTIMIZATION]";
                }
                newResponses.add(new ToolResponseMessage.ToolResponse(tr.id(), tr.name(), data));
            }
            return ToolResponseMessage.builder()
                    .responses(newResponses)
                    .build();
            } else if (msg instanceof AssistantMessage assistantMsg) {
            // Spring AI 2.0.0-M6 issue: AssistantMessage containing only tool calls has null content.
            // This can cause SQLIntegrityConstraintViolationException or unexpected null texts in DB.
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
}
