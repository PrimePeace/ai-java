package com.ai.aijava.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtils {

    /**
     * JWT 密钥（必须 >= 256 bit，即 >= 32 字符）
     */
    private static String secret;
    /**
     * Access Token 有效期（秒）
     */
    @Getter
    private static long accessTokenExpiration;
    /**
     * Refresh Token 有效期（秒）
     */
    private static long refreshTokenExpiration;

    public static void setSecret(String secret) {
        JwtUtils.secret = secret;
    }

    public static void setAccessTokenExpiration(long accessTokenExpiration) {
        JwtUtils.accessTokenExpiration = accessTokenExpiration;
    }

    public static void setRefreshTokenExpiration(long refreshTokenExpiration) {
        JwtUtils.refreshTokenExpiration = refreshTokenExpiration;
    }

    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token（包含 userId 和 username，短期有效）
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 Refresh Token（仅包含 userId，长期有效）
     */
    public static String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token，返回 Claims
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 Token 是否有效（未过期且签名正确）
     */
    public static boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取 userId
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }
}
