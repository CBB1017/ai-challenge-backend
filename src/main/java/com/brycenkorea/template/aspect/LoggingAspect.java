package com.brycenkorea.template.aspect;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    @Around("execution(* com.brycenkorea.template.controller..*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("START: {} {}", joinPoint.getSignature(), Arrays.toString(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Publisher) {
                log.info("END: {} (Publisher returned)", joinPoint.getSignature().getName());
            } else {
                log.info("END: {}", result);
            }
            return result;
        } catch (Throwable ex) {
            if (ex instanceof ApiException apiEx) {
                int code = apiEx.getCode();
                Object detail = apiEx.getDetail();
                int status = ApiResultCode.valueOfCode(code).getHttpStatus().value();

                // 로그 메시지 포맷 개선
                String msg = "[ApiException] code={}, status={}, detail={}, message={}";
                if (status >= 500) {
                    log.error(msg, code, status, detail, apiEx.getMessage(), ex);
                } else if (status >= 400) {
                    log.warn(msg, code, status, detail, apiEx.getMessage());
                } else {
                    log.info(msg, code, status, detail, apiEx.getMessage());
                }
            } else {
                // 진짜 시스템 예외
                log.error("[Exception] {}: {}", joinPoint.getSignature(), ex.getMessage(), ex);
            }
            log.info("END: (exception occurred)");
            throw ex; // rethrow!
        }
    }
}

