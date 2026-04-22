package com.brycenkorea.template.dto.response;

import java.util.UUID;

public record PromptResponse(
    String response,
    UUID messageId,
    boolean isAsync
) {
    public PromptResponse(String response) {
        this(response, null, false);
    }
}
