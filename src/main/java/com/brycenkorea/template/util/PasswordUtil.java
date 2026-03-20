package com.brycenkorea.template.util;

public class PasswordUtil {
    public static String generatePatternPassword(String name, String email) {
        String emailPrefix = "";
        if (email != null && email.length() >= 3) {
            emailPrefix = email.substring(0, 3);
        } else if (email != null) {
            emailPrefix = email; // 3자리 미만이면 전체 사용
        }
        return name + emailPrefix + "!DX2"; // ex: 문병찬bcm!DX2
    }
}