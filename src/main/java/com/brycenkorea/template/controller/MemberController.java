package com.brycenkorea.template.controller;

import com.brycenkorea.template.annotation.swagger.SwaggerDeleteResponse;
import com.brycenkorea.template.annotation.swagger.SwaggerMemberResponse;
import com.brycenkorea.template.dto.api.CommonResponse;
import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.dto.response.MemberResponse;
import com.brycenkorea.template.entity.Member;
import com.brycenkorea.template.mapper.mapstruct.MemberResponseMapper;
import com.brycenkorea.template.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearer")
@RequestMapping("/api/member")
@SwaggerMemberResponse
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberResponseMapper memberResponseMapper;

    @Operation(summary = "회원 등록", description = "새로운 회원을 등록합니다.")
    @PostMapping
    public Mono<CommonResponse<MemberResponse>> create(@RequestBody @Valid MemberRequest memberDto) {
        return memberService.save(memberDto)
                            .map(member -> CommonResponse.ok(memberResponseMapper.toResponse(member)));
    }

    @Operation(summary = "회원 단건 조회", description = "ID로 회원을 조회합니다.")
    @GetMapping("/{id}")
    public Mono<CommonResponse<MemberResponse>> read(@PathVariable @Positive Long id) {
        return memberService.findById(id)
                            .map(member -> CommonResponse.ok(memberResponseMapper.toResponse(member)));
    }

    @Operation(summary = "회원 전체 목록 조회", description = "모든 회원을 조회합니다.")
    @GetMapping
    public Mono<CommonResponse<List<MemberResponse>>> readAll() {
        return memberService.findAll()
                            .collectList() // Flux<Member>를 Mono<List<Member>>로 변환
                            .map(members -> CommonResponse.ok(memberResponseMapper.toDtoList(members)));
    }

    @Operation(summary = "회원 전체 목록 페이징 조회", description = "페이징된 회원을 조회합니다.")
    @GetMapping("/page")
    public Mono<CommonResponse<List<MemberResponse>>> readAllWithPage(Pageable pageable) {
        // WebFlux에서는 Page 객체보다 Flux를 List로 모으거나 스트리밍하는 방식을 주로 씁니다.
        return memberService.findAllWithPage(pageable)
                            .map(memberResponseMapper::toResponse)
                            .collectList()
                            .map(CommonResponse::ok);
    }

    @Operation(summary = "회원 전체 검색 조회", description = "검색된 회원을 조회합니다.")
    @GetMapping("/search")
    public Mono<CommonResponse<List<MemberResponse>>> readAllWithSearch(Pageable pageable, @RequestParam String keyword) {
        return memberService.findAllWithSearch(pageable, keyword)
                            .map(memberResponseMapper::toResponse)
                            .collectList()
                            .map(CommonResponse::ok);
    }

    @Operation(summary = "회원 정보 수정", description = "ID로 회원 정보를 수정합니다.")
    @PutMapping("/{id}")
    public Mono<CommonResponse<MemberResponse>> update(
        @PathVariable @Positive Long id,
        @RequestBody MemberRequest userDto
    ) {
        return memberService.update(id, userDto)
                            .map(member -> CommonResponse.ok(memberResponseMapper.toResponse(member)));
    }

    @Operation(summary = "회원 삭제", description = "id로 회원을 삭제합니다.")
    @SwaggerDeleteResponse
    @DeleteMapping("/{id}")
    public Mono<CommonResponse<Void>> delete(@PathVariable @Positive Long id) {
        return memberService.deleteById(id)
                            .thenReturn(CommonResponse.ok());
    }
}