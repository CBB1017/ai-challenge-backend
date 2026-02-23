package com.brycenkorea.template.dto.api;


import io.opentelemetry.api.trace.Span;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 응답", example = """
{
  "code": 200,
  "message": "Success",
  "data": Object,
  "error": null,
  "traceId": "123456789"
}
""")
public record CommonResponse<T>(
        int code,
        String message,
        T data,
        Object error,
        String traceId
) {
    public static <T> CommonResponse<T> ok(T data) {
        var ctx = Span.current().getSpanContext();
        return new CommonResponse<>(
                ApiResultCode.SUCCESS.getCode(),
                ApiResultCode.SUCCESS.getMessage(),
                data,
                null,
                ctx.getTraceId()
        );
    }
    public static <T> CommonResponse<T> ok() {
        var ctx = Span.current().getSpanContext();
        return new CommonResponse<>(
                ApiResultCode.SUCCESS.getCode(),
                ApiResultCode.SUCCESS.getMessage(),
                null,
                null,
                ctx.getTraceId()
        );
    }

    // error 객체를 직접 받는 팩토리 메서드
    public static <T> CommonResponse<T> error(ApiResultCode code, ErrorDetail errorDetail) {
        var ctx = Span.current().getSpanContext();
        return new CommonResponse<>(
                code.getCode(),
                code.getMessage(),
                null,
                errorDetail,
                ctx.getTraceId()
        );
    }

    // errorDetail을 직접 만들지 않고 detail만 파라미터로 받는 경우
    public static <T> CommonResponse<T> error(ApiResultCode code, Object detail) {
        var ctx = Span.current().getSpanContext();
        return new CommonResponse<>(
                code.getCode(),
                code.getMessage(),
                null,
                ErrorDetail.make(detail, ctx.getSpanId()),
                ctx.getTraceId()
        );
    }

    // code 값만 있을 때 (default message와 spanId로 errorDetail 생성)
    public static <T> CommonResponse<T> error(ApiResultCode code) {
        var ctx = Span.current().getSpanContext();
        return new CommonResponse<>(
                code.getCode(),
                code.getMessage(),
                null,
                ErrorDetail.make(null, ctx.getSpanId()),
                ctx.getTraceId()
        );
    }
}
