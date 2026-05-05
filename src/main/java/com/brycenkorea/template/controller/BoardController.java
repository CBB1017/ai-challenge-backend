package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.BoardPostResponse;
import com.brycenkorea.template.service.BoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Tag(name = "Board", description = "게시물 관련 API")
@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "게시판 포스트 목록 조회", description = "Redis에 저장된 모든 게시판 포스트(카테고리별 + 최근게시물) 목록을 조회합니다.")
    @GetMapping
    public Mono<Map<String, List<BoardPostResponse>>> getBoardPosts() {
        return boardService.getBoardAndRecentPosts();
    }

    @Operation(summary = "최근 게시물 목록 조회", description = "Redis에 저장된 최근 게시물 목록만 조회합니다.")
    @GetMapping("/recent")
    public Mono<List<BoardPostResponse>> getRecentPosts() {
        return boardService.getRecentPosts();
    }
}
