package com.brycenkorea.template.service;

import com.brycenkorea.template.contants.AttendanceStatus;
import com.brycenkorea.template.contants.NotificationResult;
import com.brycenkorea.template.dto.BulkImportResult;
import com.brycenkorea.template.dto.NotificationSendDto;
import com.brycenkorea.template.dto.request.AttendanceDto;
import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.entity.Attendance;
import com.brycenkorea.template.entity.Member;
import com.brycenkorea.template.mapper.mapstruct.AttendanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {
    private final AttendanceMapper attendanceMapper;
    private final AttendanceService attendanceService;
    private final SlackPushService slackPushService;
    private final MemberService memberService;

    // 1. 멤버 일괄 임포트
    public Mono<BulkImportResult> importMembers(List<MemberRequest> memberRequests) {
        return Flux.fromIterable(memberRequests)
                   .flatMap(dto -> memberService.saveOrUpdateByEmail(dto)
                                                .thenReturn(true) // 성공 시 true
                                                .onErrorResume(ex -> Mono.just(false)) // 실패 시 false (에러 로깅 등 추가 가능)
                   )
                   .collectList()
                   .map(results -> {
                       int total = results.size();
                       int success = (int) results.stream().filter(r -> r).count();
                       return new BulkImportResult(total, success, total - success, new ArrayList<>());
                   });
    }

    // 2. 근태 정보 일괄 임포트 (핵심)
    public Mono<BulkImportResult> importAttendance(Map<String, List<AttendanceDto>> teamData) {
        String today = attendanceService.getTodayDate();

        return resolveMemberMap(teamData).flatMap(memberMap -> {
            // 모든 DTO를 평면화(Flatten)하여 처리
            List<AttendanceDto> allDtos = teamData.values().stream()
                                                  .flatMap(List::stream)
                                                  .toList();

            return Flux.fromIterable(allDtos)
                       .flatMap(dto -> {
                           String key = dto.getMemberName() + "::" + dto.getPosition();
                           Member member = memberMap.get(key);

                           if (member == null) {
                               String error = "멤버 조회 없음: " + key;
                               return sendNotificationIfRequired(null, dto.getStatus(), error)
                                   .thenReturn(BulkResult.fail(key, error));
                           }

                           // 엔티티 변환 및 알림 발송
                           Attendance entity = attendanceMapper.toEntity(dto, member, today);
                           return sendNotificationIfRequired(member, dto.getStatus(), null)
                               .thenReturn(BulkResult.success(entity));
                       })
                       .collectList()
                       .flatMap(results -> {
                           // 1. 성공한 엔티티 리스트 추출 (성공 여부는 r.success()로 체크)
                           List<Attendance> toSave = results.stream()
                                                            .filter(BulkResult::success) // success() 메서드가 true인 것만
                                                            .map(BulkResult::entity)    // entity() 메서드로 객체 추출
                                                            .filter(Objects::nonNull)   // 만약을 대비한 null 체크
                                                            .toList();

                           // 2. 실패한 아이템 리스트 추출 (r.success()가 false인 것)
                           List<BulkImportResult.FailedItem> failedItems = results.stream()
                                                                                  .filter(r -> !r.success())
                                                                                  .map(r -> new BulkImportResult.FailedItem(r.key(), r.reason()))
                                                                                  .toList();

                           return attendanceService.saveOrUpdateAttendanceList(toSave)
                                                   .thenReturn(BulkImportResult.builder()
                                                                               .total(results.size())
                                                                               .success(toSave.size())
                                                                               .fail(failedItems.size())
                                                                               .failedItems(failedItems)
                                                                               .build());
                       });
        });
    }

    // 알림 발송 로직의 비동기화
    private Mono<Void> sendNotificationIfRequired(Member member, AttendanceStatus status, String errorReason) {
        if (status != null && status.name().endsWith("_NOTIFICATION")) {
            NotificationResult result = (errorReason == null) ? NotificationResult.SUCCESS : NotificationResult.FAIL;
            NotificationSendDto notifyDto = new NotificationSendDto(member, status, status.getMessage(), result, errorReason);
            return slackPushService.sendSlackPush(notifyDto);
        }
        return Mono.empty();
    }

    // 내부 처리를 위한 임시 헬퍼 클래스
    private record BulkResult(Attendance entity, boolean success, String key, String reason) {
        public static BulkResult success(Attendance entity) {
            return new BulkResult(entity, true, null, null);
        }

        public static BulkResult fail(String key, String reason) {
            return new BulkResult(null, false, key, reason);
        }
    }

    /**
     * DB에 저장된 멤버들을 조회하여 "이름::직급"을 키로 하는 Map으로 반환합니다.
     */
    private Mono<Map<String, Member>> resolveMemberMap(Map<String, List<AttendanceDto>> teamData) {
        Set<String> nameSet = new HashSet<>();
        Set<String> positionSet = new HashSet<>();

        // 1. 입력받은 데이터에서 이름과 직급 세트 추출 (메모리 작업)
        teamData.values().forEach(list -> list.forEach(dto -> {
            if (StringUtils.isNotBlank(dto.getMemberName())) {
                nameSet.add(dto.getMemberName());
            }
            if (StringUtils.isNotBlank(dto.getPosition())) {
                positionSet.add(dto.getPosition());
            }
        }));

        if (nameSet.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        // 2. Repository에서 Flux<Member>로 조회 후 Mono<Map>으로 수집(Collect)
        return memberService.findAllByNameInAndPositionIn(nameSet, positionSet)
                               .collectMap(
                                   member -> member.getName() + "::" + member.getPosition(), // Key
                                   member -> member                                         // Value
                               );
    }
}