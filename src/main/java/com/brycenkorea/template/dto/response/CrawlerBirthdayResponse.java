package com.brycenkorea.template.dto.response;

import java.util.List;

public record CrawlerBirthdayResponse(
    String status,
    String message,
    List<BirthdayResponse> data
) {}