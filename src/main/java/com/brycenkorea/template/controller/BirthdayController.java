package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.BirthdayResponse;
import com.brycenkorea.template.service.BirthdayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Birthday", description = "생일자 관련 API")
@RestController
@RequestMapping("/api/v1/birthdays")
@RequiredArgsConstructor
public class BirthdayController {

    private final BirthdayService birthdayService;

    @Operation(summary = "이번 달 생일자 목록 조회", description = "Redis에 저장된 이번 달 생일자 목록을 조회합니다.")
    @GetMapping
    public Mono<List<BirthdayResponse>> getBirthdays() {
        return birthdayService.getBirthdays();
    }
}
