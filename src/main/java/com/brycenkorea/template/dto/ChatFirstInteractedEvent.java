package com.brycenkorea.template.dto;

import java.util.UUID;

public record ChatFirstInteractedEvent(
    UUID roomId,
    String userPrompt,
    String aiResponse
) {}