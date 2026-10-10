package com.ai.aijava.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 注册校验与 Refresh Token 签发时间比较。
 */
public final class CredentialRules {

    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_]{4,32}$";
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";

    private CredentialRules() {
    }

    public static boolean isUsernameValid(String username) {
        return username != null && username.matches(USERNAME_PATTERN);
    }

    public static boolean isPasswordValid(String password) {
        return password != null && password.matches(PASSWORD_PATTERN);
    }

    /**
     * 未设置失效时间时放行。签发时间必须晚于失效时间，相等视为已退出。
     */
    public static boolean isRefreshIssuedAfter(Date issuedAt, LocalDateTime invalidBefore) {
        if (invalidBefore == null) {
            return true;
        }
        if (issuedAt == null) {
            return false;
        }
        LocalDateTime issued = LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault());
        return issued.isAfter(invalidBefore);
    }
}
