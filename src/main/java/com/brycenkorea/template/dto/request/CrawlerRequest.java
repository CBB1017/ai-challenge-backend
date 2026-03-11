package com.brycenkorea.template.dto.request;

import java.util.Map;

public record CrawlerRequest(
    String action,
    Map<String, Object> params
) {}
