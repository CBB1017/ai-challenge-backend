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

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
// @formatter:off
@ApiResponse(
    responseCode = "200",
    description = "삭제 성공",
    content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = CommonResponse.class),
        examples = @ExampleObject(
            value = """
                {
                    "code": 200,
                    "message": "Success",
                    "data": null,
                    "error": null,
                    "traceId": "abcd123"
                }
            """
        )
    )
)
public @interface SwaggerDeleteResponse {}
