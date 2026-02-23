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

import java.util.List;

@RestController
@SecurityRequirement(name = "bearer")
@RequestMapping("/api/member")
@SwaggerMemberResponse
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberResponseMapper memberResponseMapper;

    // JPA 방식
    @Operation(summary = "회원 등록", description = "새로운 회원을 등록합니다.")
    @PostMapping
    public CommonResponse<MemberResponse> create(@RequestBody @Valid MemberRequest memberDto) {
        Member member = memberService.save(memberDto);
        return CommonResponse.ok(memberResponseMapper.toResponse(member));
    }

    @Operation(
        summary = "회원 단건 조회", description = "ID로 회원을 조회합니다."
    )
    @GetMapping("/{id}")
    public CommonResponse<MemberResponse> read(
        @Parameter(
            description = "조회할 회원 ID", example = "1"
        ) @PathVariable @Positive Long id
    )
    {
        Member member = memberService.findById(id);
        return CommonResponse.ok(memberResponseMapper.toResponse(member));
    }

    @Operation(summary = "회원 전체 목록 조회", description = "모든 회원을 조회합니다.")
    @GetMapping
    public CommonResponse<List<MemberResponse>> readAll() {
        List<Member> members = memberService.findAll();
        return CommonResponse.ok(memberResponseMapper.toDtoList(members));
    }

    @Operation(summary = "회원 전체 목록 페이징 조회", description = "페이징된 회원을 조회합니다.")
    @GetMapping("/page")
    public CommonResponse<Page<MemberResponse>> readAllWithPage(Pageable pageable) {
        Page<Member> members = memberService.findAllWithPage(pageable);
        return CommonResponse.ok(members.map(memberResponseMapper::toResponse));
    }

    @Operation(summary = "회원 전체 검색 조회", description = "검색된 회원을 조회합니다.")
    @GetMapping("/search")
    public CommonResponse<Page<MemberResponse>> readAllWithSearch(Pageable pageable, @RequestParam String keyword) {
        Page<Member> members = memberService.findAllWithSearch(pageable, keyword);
        return CommonResponse.ok(members.map(memberResponseMapper::toResponse));
    }

    @Operation(summary = "회원 정보 수정", description = "ID로 회원 정보를 수정합니다.")
    @PutMapping("/{id}")
    public CommonResponse<MemberResponse> update(
        @Parameter(description = "수정할 회원 ID", example = "1") @PathVariable @Positive Long id,
        @RequestBody MemberRequest userDto
    )
    {
        Member member = memberService.update(id, userDto);
        return CommonResponse.ok(memberResponseMapper.toResponse(member));
    }

    @Operation(summary = "회원 삭제", description = "id로 회원을 삭제합니다.")
    @SwaggerDeleteResponse
    @DeleteMapping("/{id}")
    public CommonResponse<Void> delete(
        @Parameter(description = "삭제할 회원 ID", example = "1") @PathVariable @Positive Long id
    )
    {
        memberService.deleteById(id);
        return CommonResponse.ok();
    }
}