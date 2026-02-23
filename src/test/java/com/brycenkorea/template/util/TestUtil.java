package com.brycenkorea.template.util;

import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.lang.reflect.Method;

public class TestUtil {
    public static MethodArgumentNotValidException createMethodArgumentNotValidException(String message) {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "target");
        errors.addError(new FieldError("target", "field", message));
        try {
            Method method = TestUtil.class.getMethod("dummyMethod");
            return new MethodArgumentNotValidException(null, errors);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static BindException createBindException(String message) {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "target");
        errors.addError(new FieldError("target", "field", message));
        return new BindException(errors);
    }

    // just for method reference (not used in test)
    public void dummyMethod() {}
}
