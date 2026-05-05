package com.brycenkorea.template.dto.response;

import java.util.List;
import java.util.Map;

public record CrawlerBoardResponse(
    String status,
    String message,
    Map<String, List<BoardPostResponse>> data
) {}
