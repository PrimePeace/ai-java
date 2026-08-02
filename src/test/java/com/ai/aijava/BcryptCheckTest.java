package com.ai.aijava;

import com.ai.aijava.utils.BCryptUtils;
import org.junit.jupiter.api.Test;

public class BcryptCheckTest {
    @Test
    void checkBcrypt() {
        String hash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        System.out.println("=== BCrypt Check ===");
        System.out.println("'admin123' matches: " + BCryptUtils.matches("admin123", hash));
        System.out.println("'123456' matches: " + BCryptUtils.matches("123456", hash));
        String newHash = BCryptUtils.encode("admin123");
        System.out.println("New hash for 'admin123': " + newHash);
        System.out.println("Verify: " + BCryptUtils.matches("admin123", newHash));
    }
}
