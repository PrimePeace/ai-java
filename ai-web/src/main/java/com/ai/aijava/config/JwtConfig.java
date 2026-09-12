package com.ai.aijava.config;

import com.ai.aijava.utils.JwtUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置初始化，启动时将配置值注入 JwtUtils 静态字段
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;

    @PostConstruct
    public void init() {
        JwtUtils.setSecret(secret);
        JwtUtils.setAccessTokenExpiration(accessTokenExpiration);
        JwtUtils.setRefreshTokenExpiration(refreshTokenExpiration);
    }
}