package com.brycenkorea.template.dto.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ApiResultCode {
    SUCCESS(200, "성공", HttpStatus.OK), NO_CONTENT(204, "콘텐트 없음", HttpStatus.NO_CONTENT),

    VALIDATION_ERROR(400, "올바른 데이터로 요청해 주세요.", HttpStatus.BAD_REQUEST), INVALID_TOKEN(
        400,
        "인증에 실패했습니다.",
        HttpStatus.BAD_REQUEST
    ), INVALID_PARAMETER(400, "올바르지 않은 파라미터입니다.", HttpStatus.BAD_REQUEST), UNAUTHORIZED(
        401,
        "인증이 필요합니다.",
        HttpStatus.UNAUTHORIZED
    ), FORBIDDEN(403, "권한이 없습니다.", HttpStatus.FORBIDDEN), MEMBER_NOT_FOUND(
        404,
        "사용자 없음",
        HttpStatus.OK
    ), MEMBER_ALREADY_EXISTS(409, "이미 존재하는 사용자입니다.", HttpStatus.CONFLICT),

    INTERNAL_ERROR(500, "서버 오류입니다.", HttpStatus.INTERNAL_SERVER_ERROR), SQL_SYNTAX_ERROR(
        501,
        "SQL 구문 오류가 발생했습니다.",
        HttpStatus.INTERNAL_SERVER_ERROR
    ), MEMBER_INSERT_FAIL(502, "사용자 저장 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    FATAL_ERROR(900, "알 수 없는 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);


    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    public static ApiResultCode valueOfCode(int code) {
        for (ApiResultCode value : values()) {
            if (value.getCode() == code) {
                return value;
            }
        }
        return ApiResultCode.FATAL_ERROR; // 못 찾으면 기본값 반환
    }
}
