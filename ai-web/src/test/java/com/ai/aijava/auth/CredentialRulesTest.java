package com.ai.aijava.auth;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialRulesTest {

    @Test
    void usernameAcceptsLettersDigitsUnderscoreBetween4And32() {
        assertTrue(CredentialRules.isUsernameValid("user_01"));
        assertFalse(CredentialRules.isUsernameValid("abc"));
        assertFalse(CredentialRules.isUsernameValid("user-name"));
        assertFalse(CredentialRules.isUsernameValid("用户名测试"));
    }

    @Test
    void passwordRequiresLetterAndDigitAndMinLength8() {
        assertTrue(CredentialRules.isPasswordValid("abc12345"));
        assertFalse(CredentialRules.isPasswordValid("abcdefgh"));
        assertFalse(CredentialRules.isPasswordValid("12345678"));
        assertFalse(CredentialRules.isPasswordValid("ab12"));
    }

    @Test
    void refreshTokenIssuedAtOrBeforeInvalidTimeIsRejected() {
        LocalDateTime invalidBefore = LocalDateTime.of(2026, 10, 10, 12, 0, 0);
        Date sameInstant = Date.from(invalidBefore.atZone(ZoneId.systemDefault()).toInstant());
        Date later = Date.from(invalidBefore.plusSeconds(1).atZone(ZoneId.systemDefault()).toInstant());
        assertFalse(CredentialRules.isRefreshIssuedAfter(sameInstant, invalidBefore));
        assertTrue(CredentialRules.isRefreshIssuedAfter(later, invalidBefore));
        assertTrue(CredentialRules.isRefreshIssuedAfter(sameInstant, null));
    }
}
