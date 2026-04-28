package com.brycenkorea.template.dto;

import java.util.UUID;

public record ChatFirstInteractedEvent(
    UUID roomId,
    String userId,
    String userPrompt,
    String aiResponse,
    String language
) {}