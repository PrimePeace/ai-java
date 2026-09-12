package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求
 * 用于 AccessToken 过期后，通过 RefreshToken 重新获取访问凭证。
 */
@Data
public class RefreshTokenRequest {

    /** 刷新 Token（必填，登录时返回的 refreshToken） */
    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}