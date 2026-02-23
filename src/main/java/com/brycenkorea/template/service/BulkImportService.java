package com.brycenkorea.template.service;

import com.brycenkorea.template.contants.AttendanceStatus;
import com.brycenkorea.template.contants.NotificationResult;
import com.brycenkorea.template.dto.BulkImportResult;
import com.brycenkorea.template.dto.NotificationSendDto;
import com.brycenkorea.template.dto.request.AttendanceDto;
import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.entity.Attendance;
import com.brycenkorea.template.entity.Member;
import com.brycenkorea.template.exception.ApiException;
import com.brycenkorea.template.mapper.mapstruct.AttendanceMapper;
import com.brycenkorea.template.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {
    private final MemberRepository memberRepository;
    private final AttendanceMapper attendanceMapper;
    private final AttendanceService attendanceService;
    private final SlackPushService slackPushService;
    private final MemberService memberService;

    public BulkImportResult importMembers(List<MemberRequest> memberRequests) {
        int total = memberRequests.size();
        int success = 0;
        List<BulkImportResult.FailedItem> failedList = new ArrayList<>();
        for (MemberRequest dto : memberRequests) {
            try {
                memberService.saveOrUpdateByEmail(dto);
                success++;
            } catch (ApiException ex) {
                failedList.add(new BulkImportResult.FailedItem(dto.getName() + dto.getPosition(), ex.getMessage()));
            }
        }
        return new BulkImportResult(total, success, total - success, failedList);
    }

    public BulkImportResult importAttendance(Map<String, List<AttendanceDto>> teamData) {
        Map<String, Member> memberMap = resolveMemberMap(teamData);

        List<Attendance> attendanceList = new ArrayList<>();
        List<BulkImportResult.FailedItem> failedItems = new ArrayList<>();
        int total = 0;
        int success = 0;

        for (List<AttendanceDto> dtoList : teamData.values()) {
            for (AttendanceDto dto : dtoList) {
                total++;
                String key = dto.getMemberName() + "::" + dto.getPosition();
                Member member = memberMap.get(key);
                AttendanceStatus status = dto.getStatus();

                if (member == null) {
                    String errorReason = "멤버 조회 없음: " + key;
                    handleFailedAttendance(dto, errorReason, failedItems);
                    sendAttendanceNotification(null, status, errorReason);
                    continue;
                }

                Attendance entity = attendanceMapper.toEntity(dto, member, attendanceService.getTodayDate());
                attendanceList.add(entity);

                sendAttendanceNotification(member, status, null);
                success++;
            }
        }

        attendanceService.saveOrUpdateAttendanceList(attendanceList);

        return BulkImportResult.builder()
                               .total(total)
                               .success(success)
                               .fail(total - success)
                               .failedItems(failedItems)
                               .build();
    }

    private Map<String, Member> resolveMemberMap(Map<String, List<AttendanceDto>> teamData) {
        Set<String> nameSet = new HashSet<>();
        Set<String> positionSet = new HashSet<>();
        teamData.values().forEach(list -> list.forEach(dto -> {
            nameSet.add(dto.getMemberName());
            positionSet.add(dto.getPosition());
        }));
        return memberRepository.findAllByNameInAndPositionIn(nameSet, positionSet)
                               .stream()
                               .collect(Collectors.toMap(m -> m.getName() + "::" + m.getPosition(), m -> m));
    }

    private void handleFailedAttendance(
        AttendanceDto dto,
        String errorReason,
        List<BulkImportResult.FailedItem> failedItems
    )
    {
        failedItems.add(BulkImportResult.FailedItem.builder()
                                                   .keyField(dto.getMemberName() + dto.getPosition())
                                                   .reason(errorReason)
                                                   .build());
    }

    private void sendAttendanceNotification(Member member, AttendanceStatus status, String errorReason)
    {
        if (status != null && status.name().endsWith("_NOTIFICATION")) {
            NotificationResult result = (errorReason == null) ? NotificationResult.SUCCESS : NotificationResult.FAIL;
            String msg = status.getMessage();
            NotificationSendDto notifyDto = new NotificationSendDto(member, status, msg, result, errorReason);
            slackPushService.sendSlackPush(notifyDto);
        }
    }
}