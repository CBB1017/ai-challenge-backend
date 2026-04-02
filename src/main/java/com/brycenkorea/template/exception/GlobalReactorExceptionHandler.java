package com.brycenkorea.template.exception;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.api.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalReactorExceptionHandler {

    // WebFlux에서는 WebExchangeBindException이 발생합니다.
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<CommonResponse<Void>>> handleValidation(WebExchangeBindException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        log.warn("Validation failed: {}", errorMessage);

        return Mono.just(ResponseEntity.badRequest()
            .body(CommonResponse.error(ApiResultCode.VALIDATION_ERROR, errorMessage)));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public Mono<ResponseEntity<CommonResponse<Void>>> handleNoSuchElement() {
        return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT)
            .body(CommonResponse.error(ApiResultCode.NO_CONTENT)));
    }

    @ExceptionHandler(ApiException.class)
    public Mono<ResponseEntity<CommonResponse<Void>>> handleApiException(ApiException ex) {
        ApiResultCode code = ApiResultCode.valueOfCode(ex.getCode());
        return Mono.just(ResponseEntity.status(code.getHttpStatus()).body(CommonResponse.error(code, ex.getDetail())));
    }

    // DB 제약 위반 등
    @ExceptionHandler({DataIntegrityViolationException.class, DuplicateKeyException.class})
    public Mono<ResponseEntity<CommonResponse<Void>>> handleConstraint(Exception ex) {
        log.error("DB Constraint Violation: {}", ex.getMessage());
        return Mono.just(ResponseEntity.badRequest().body(CommonResponse.error(ApiResultCode.MEMBER_ALREADY_EXISTS)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<CommonResponse<Void>>> handleAll(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        return Mono.just(ResponseEntity.internalServerError().body(CommonResponse.error(ApiResultCode.FATAL_ERROR)));
    }
}