package com.brycenkorea.template.dto.response;

import lombok.Builder;

@Builder
public record BirthdayResponse(
    String day,
    String name,
    String position,
    String department
) {}
