package com.brycenkorea.template.exception;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.api.CommonResponse;
import com.brycenkorea.template.dto.api.ErrorDetail;
import com.brycenkorea.template.util.TestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_withMethodArgumentNotValidException_returns_bad_request() {
        // Given: 유효성 검증에 실패한 상황 (예: @Valid 등에서 바인딩 에러 발생)
        MethodArgumentNotValidException ex = TestUtil.createMethodArgumentNotValidException("테스트 에러메시지");

        // When: GlobalExceptionHandler의 handleValidation이 해당 예외를 처리할 때
        ResponseEntity<CommonResponse<Void>> resp = handler.handleValidation(ex);

        // Then: 400(BAD_REQUEST) 상태와, ApiResultCode.VALIDATION_ERROR 코드/메시지, 그리고 error.detail에 구체적인 에러 메시지가 담긴다
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().code()).isEqualTo(ApiResultCode.VALIDATION_ERROR.getCode());
        assertThat(resp.getBody().message()).isEqualTo(ApiResultCode.VALIDATION_ERROR.getMessage());
        assertThat((String) ((ErrorDetail) resp.getBody().error()).detail()).isEqualTo("테스트 에러메시지");
    }

    @Test
    void handleValidation_withBindException_returns_bad_request() {
        // Given: 일반적인 바인딩 예외가 발생한 상황 (주로 폼 데이터 바인딩 시)
        BindException ex = TestUtil.createBindException("Bind 에러메시지");

        // When: GlobalExceptionHandler의 handleValidation이 BindException을 처리하면
        ResponseEntity<CommonResponse<Void>> resp = handler.handleValidation(ex);

        // Then: 400(BAD_REQUEST), ApiResultCode.VALIDATION_ERROR, error.detail에 구체적인 메시지
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().code()).isEqualTo(ApiResultCode.VALIDATION_ERROR.getCode());
        assertThat(resp.getBody().message()).isEqualTo(ApiResultCode.VALIDATION_ERROR.getMessage());
        assertThat((String) ((ErrorDetail) resp.getBody().error()).detail()).isEqualTo("Bind 에러메시지");
    }

    @Test
    void handleValidation_withNoSuchElementException_returns_internal_server_error() {
        // Given: NoSuchElementException이 발생하는 경우 (ex. Optional.get()에서 값이 없을 때)
        // When: handler가 해당 예외를 처리하면
        ResponseEntity<CommonResponse<Void>> resp = handler.handleValidation();

        // Then: 500(INTERNAL_SERVER_ERROR), ApiResultCode.NO_CONTENT, error.detail이 null (상세 메시지 없음)
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().code()).isEqualTo(ApiResultCode.NO_CONTENT.getCode());
        assertThat(resp.getBody().message()).isEqualTo(ApiResultCode.NO_CONTENT.getMessage());
        assertThat((String) ((ErrorDetail) resp.getBody().error()).detail()).isNull();
    }

    @Test
    void handleApiException_returns_status_from_code() {
        // Given: 비즈니스 로직에서 ApiException이 발생한 경우 (ex. 사용자 없음 등)
        ApiException ex = new ApiException(ApiResultCode.MEMBER_NOT_FOUND, "상세메시지");

        // When: handler가 ApiException을 처리하면
        ResponseEntity<CommonResponse<Void>> resp = handler.handleApiException(ex);

        // Then: status는 ApiResultCode.MEMBER_NOT_FOUND의 httpStatus, message는 지정된 메시지, error.detail에 상세메시지
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().code()).isEqualTo(ApiResultCode.MEMBER_NOT_FOUND.getCode());
        assertThat(resp.getBody().message()).isEqualTo(ApiResultCode.MEMBER_NOT_FOUND.getMessage());
        assertThat((String) ((ErrorDetail) resp.getBody().error()).detail()).isEqualTo("상세메시지");
    }

    @Test
    void handleConstraint_returns_bad_request() {
        // Given: DB 중복키 제약 등으로 DuplicateKeyException이 발생한 경우
        Exception ex = new org.springframework.dao.DuplicateKeyException("중복키 오류");

        // When: handler가 해당 예외를 처리하면
        ResponseEntity<CommonResponse<Void>> resp = handler.handleConstraint(ex);

        // Then: 400(BAD_REQUEST), ApiResultCode.MEMBER_ALREADY_EXISTS, error.detail에 에러 메시지("중복키" 포함)
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().code()).isEqualTo(ApiResultCode.MEMBER_ALREADY_EXISTS.getCode());
        assertThat(resp.getBody().message()).isEqualTo(ApiResultCode.MEMBER_ALREADY_EXISTS.getMessage());
        assertThat(resp.getBody().error()).isNotNull();
        assertThat(((ErrorDetail) resp.getBody().error()).detail()).isNull();
    }

    @Test
    void handleBadSql_returns_internal_server_error() {
        // Given: SQL 문법 오류로 BadSqlGrammarException이 발생한 경우
        org.springframework.jdbc.BadSqlGrammarException ex = new org.springframework.jdbc.BadSqlGrammarException(
            "task",
            "SELECT * FROM",
            new SQLException("SQL오류")
        );

        // When: handler가 해당 예외를 처리하면
        ResponseEntity<CommonResponse<Void>> resp = handler.handleBadSql(ex);

        // Then: 500(INTERNAL_SERVER_ERROR), ApiResultCode.SQL_SYNTAX_ERROR, error.detail에 쿼리 내용이 포함됨
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().code()).isEqualTo(ApiResultCode.SQL_SYNTAX_ERROR.getCode());
        assertThat(resp.getBody().message()).isEqualTo(ApiResultCode.SQL_SYNTAX_ERROR.getMessage());
        assertThat(resp.getBody().error()).isNotNull();
        assertThat(((ErrorDetail) resp.getBody().error()).detail()).isNull();
    }

    @Test
    void handleAll_returns_internal_server_error() {
        // Given: 예상하지 못한 Exception이 발생한 경우
        Exception ex = new Exception("어떤 에러");

        // When: handler가 해당 예외를 처리하면
        ResponseEntity<CommonResponse<Void>> resp = handler.handleAll(ex);

        // Then: 500(INTERNAL_SERVER_ERROR), ApiResultCode.FATAL_ERROR, error.detail에 Exception 메시지가 담김
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().code()).isEqualTo(ApiResultCode.FATAL_ERROR.getCode());
        assertThat(resp.getBody().message()).isEqualTo(ApiResultCode.FATAL_ERROR.getMessage());
        assertThat(resp.getBody().error()).isNotNull();
        assertThat(((ErrorDetail) resp.getBody().error()).detail()).isNull();
    }
}
