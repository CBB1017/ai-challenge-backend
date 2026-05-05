package com.brycenkorea.template.dto.response;

import java.util.List;

public record CrawlerRecentBoardResponse(
    String status,
    String message,
    List<BoardPostResponse> data
) {}
