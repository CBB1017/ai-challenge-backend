package com.brycenkorea.template.annotation.swagger;

import com.brycenkorea.template.dto.api.CommonResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
    responseCode = "200", description = "성공", content = @Content(
    mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class), examples = @ExampleObject(
    name = "성공", value = """
        {
          "code": 200,
          "message": "Success",
          "data": {
            "total": 10,
            "exe": 8,
            "failedMembers": [
              "이도경과장",
              "홍길동신입사원"
            ]
          },
          "error": null,
          "traceId": "abcd123"
        }
    """
)
)
)
@ApiResponse(
    responseCode = "404", description = "리소스 없음", content = @Content(
    mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class), examples = @ExampleObject(
    name = "리소스 없음", value = """
        {
          "code": 404,
          "message": "Member not found",
          "data": null,
          "error": {
            "detail": "userId: 99",
            "spanId": "bcd123"
          },
          "traceId": "abcde"
        }
    """
)
)
)
@ApiResponse(
    responseCode = "403", description = "권한 없음", content = @Content(
    mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class), examples = @ExampleObject(
    name = "권한 없음", value = """
        {
          "code": 403,
          "message": "Forbidden",
          "data": null,
          "error": {
            "detail": "권한 없음",
            "spanId": "bcd124"
          },
          "traceId": "abcdef"
        }
    """
)
)
)
public @interface SwaggerAttendanceResponse {}