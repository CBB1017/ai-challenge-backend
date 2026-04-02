package com.brycenkorea.template.exception;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.api.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

//@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<CommonResponse<Void>> handleValidation(Exception ex) {
        String errorMessage = null;
        if (ex instanceof MethodArgumentNotValidException manve) {
            errorMessage = manve.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        } else if (ex instanceof BindException be) {
            errorMessage = be.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(CommonResponse.error(ApiResultCode.VALIDATION_ERROR, errorMessage));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<CommonResponse<Void>> handleValidation() {
        return ResponseEntity.internalServerError().body(CommonResponse.error(ApiResultCode.NO_CONTENT));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonResponse<Void>> handleApiException(ApiException ex) {
        ApiResultCode code = ApiResultCode.valueOfCode(ex.getCode());
        return ResponseEntity.status(code.getHttpStatus()).body(CommonResponse.error(code, ex.getDetail()));
    }

    // JPA/DB 제약 위반 (중복 등)
    @ExceptionHandler({DataIntegrityViolationException.class, DuplicateKeyException.class})
    public ResponseEntity<CommonResponse<Void>> handleConstraint(Exception ex) {
        log.error(ex.getLocalizedMessage());
        // 상황별로 코드 정교하게 분리할 수도 있음
        return ResponseEntity.badRequest().body(CommonResponse.error(ApiResultCode.MEMBER_ALREADY_EXISTS));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<CommonResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.error(ex.getLocalizedMessage());
        return ResponseEntity.badRequest()
            .body(CommonResponse.error(ApiResultCode.INVALID_TOKEN, ex.getLocalizedMessage()));
    }

    // MyBatis 문법 에러 (SQL 오타 등)
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<CommonResponse<Void>> handleBadSql(BadSqlGrammarException ex) {
        log.error(ex.getLocalizedMessage());
        return ResponseEntity.internalServerError().body(CommonResponse.error(ApiResultCode.SQL_SYNTAX_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleAll(Exception ex) {
        log.error(ex.getLocalizedMessage());
        return ResponseEntity.internalServerError().body(CommonResponse.error(ApiResultCode.FATAL_ERROR));
    }
}