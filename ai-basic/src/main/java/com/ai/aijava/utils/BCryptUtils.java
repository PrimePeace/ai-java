package com.ai.aijava.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptUtils {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 对原始密码进行 BCrypt 加密
     *
     * @param rawPassword 原始明文密码
     * @return BCrypt 加密后的密文
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }
    /**
     * 校验明文密码与 BCrypt 密文是否匹配
     *
     * @param rawPassword     原始明文密码
     * @param encodedPassword BCrypt 加密后的密文
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
