package com.brycenkorea.template.exception;

import com.brycenkorea.template.dto.api.ApiResultCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final int code;
    private final String message;
    private final transient Object detail; // 잘못된 데이터, 추가 정보 등

    public ApiException(ApiResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
        this.detail = null;
    }

    public ApiException(ApiResultCode resultCode, Object detail) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
        this.detail = detail;
    }

    public ApiException(int customCode, String message, Object detail) {
        super(message);
        this.code = customCode;
        this.message = message;
        this.detail = detail;
    }
}
