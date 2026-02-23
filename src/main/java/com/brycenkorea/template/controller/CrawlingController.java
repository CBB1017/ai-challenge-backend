package com.brycenkorea.template.controller;

import com.brycenkorea.template.annotation.swagger.SwaggerAttendanceResponse;
import com.brycenkorea.template.dto.BulkImportResult;
import com.brycenkorea.template.dto.api.CommonResponse;
import com.brycenkorea.template.dto.request.AttendanceDto;
import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.dto.request.member.TeamWithMembersRequest;
import com.brycenkorea.template.service.BulkImportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crawling")
@RequiredArgsConstructor
public class CrawlingController {
    private final BulkImportService bulkImportService;

    @Operation(summary = "근태 데이터 등록(from crawling)", description = "출퇴근 기록을 등록합니다.")
    @SwaggerAttendanceResponse
    @PostMapping("/attendance/import")
    public CommonResponse<BulkImportResult> importAttendance(
        @RequestBody @Valid Map<String, List<@Valid AttendanceDto>> teamData
    )
    {
        BulkImportResult result = bulkImportService.importAttendance(teamData);
        return CommonResponse.ok(result);
    }

    @Operation(summary = "회원 등록(from crawling)", description = "회원들을 등록합니다.")
    @PostMapping("/member/import")
    public CommonResponse<BulkImportResult> importMembers(@RequestBody List<TeamWithMembersRequest> teamsDto) {
        List<MemberRequest> dx2TeamMembers = teamsDto.stream()
                                                     .filter(t -> "DX 2Team".equals(t.getTeam()))
                                                     .flatMap(t -> t.getMembers().stream())
                                                     .toList();

        BulkImportResult result = bulkImportService.importMembers(dx2TeamMembers);
        return CommonResponse.ok(result);
    }
}
