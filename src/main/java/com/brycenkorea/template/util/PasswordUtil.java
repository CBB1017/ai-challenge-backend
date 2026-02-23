package com.brycenkorea.template.util;


import org.springframework.security.crypto.password.PasswordEncoder;

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

    // 비밀번호 암호화: 이미 암호화된 값(bcrypt/sha256 등)은 그대로 둠
    public static String encodeIfNeeded(String password, PasswordEncoder encoder) {
        if (password == null) return null;
        // bcrypt
        if (password.matches("^\\$2[aby]\\$.{56}$")) return password;
        // sha256+base64
        if (password.matches("^[A-Za-z0-9+/]{40,44}={0,2}$")) return password;
        // 그 외엔 암호화
        return encoder.encode(password);
    }
}