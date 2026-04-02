package com.brycenkorea.template.dto.api;

public record ErrorDetail(Object detail, String spanId) {
    public static ErrorDetail make(Object detail, String spanId) {
        return new ErrorDetail(detail, spanId);
    }
}
