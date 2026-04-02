package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.BulkImportResult;
import com.brycenkorea.template.dto.api.CommonResponse;
import com.brycenkorea.template.dto.request.AttendanceDto;
import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.dto.request.member.TeamWithMembersRequest;
import com.brycenkorea.template.service.BulkImportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crawling")
@RequiredArgsConstructor
@Slf4j
public class CrawlingController {
    private final BulkImportService bulkImportService;

    @Operation(summary = "근태 데이터 등록(from crawling)", description = "출퇴근 기록을 등록합니다.")
    @PostMapping("/attendance/import")
    public Mono<CommonResponse<BulkImportResult>> importAttendance(
        @RequestBody @Valid Map<String, List<@Valid AttendanceDto>> teamData
    )
    {
        // 비동기 서비스 호출 및 응답 매핑
        return bulkImportService.importAttendance(teamData).map(CommonResponse::ok);
    }

    @Operation(summary = "회원 등록(from crawling)", description = "회원들을 등록합니다.")
    @PostMapping("/member/import")
    public Mono<CommonResponse<BulkImportResult>> importMembers(@RequestBody List<TeamWithMembersRequest> teamsDto) {
        // 스트림 처리는 기존과 동일하되, 최종 서비스 호출만 비동기로 연결
        List<MemberRequest> dx2TeamMembers = teamsDto.stream()
            .filter(t -> "DX 2Team".equals(t.getTeam()))
            .flatMap(t -> t.getMembers().stream())
            .toList();

        return bulkImportService.importMembers(dx2TeamMembers).map(CommonResponse::ok);
    }
}