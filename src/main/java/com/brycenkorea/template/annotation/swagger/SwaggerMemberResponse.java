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
// @formatter:off
@ApiResponse(
    responseCode = "200",
    description = "성공",
    content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = CommonResponse.class),
        examples = @ExampleObject(
            name = "성공",
            value = """
                    {
                      "code": 200,
                      "message": "Success",
                      "data": {
                        "id": 1,
                        "name": "testuser",
                        "email": "test@example.com"
                      },
                      "error": null,
                      "traceId": "abcd123"
                    }
                """
        )
    )
)
// @formatter:off
@ApiResponse(
    responseCode = "404",
    description = "리소스 없음",
    content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = CommonResponse.class),
        examples = @ExampleObject(
            name = "실패",
            value = """
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
// @formatter:off
@ApiResponse(
    responseCode = "403",
    description = "권한 없음",
    content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = CommonResponse.class),
        examples = @ExampleObject(
            name = "실패",
            value = """
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
public @interface SwaggerMemberResponse {}
