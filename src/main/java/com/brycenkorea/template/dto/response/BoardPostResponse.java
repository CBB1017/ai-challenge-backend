package com.brycenkorea.template.dto.response;

import lombok.Builder;

@Builder
public record BoardPostResponse(
    String title,
    String author,
    String date,
    String url
) {}
