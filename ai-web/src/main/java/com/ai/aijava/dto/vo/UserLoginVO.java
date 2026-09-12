package com.ai.aijava.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录响应
 * 登录成功后的返回值，包含双 Token（AccessToken + RefreshToken）、
 * 过期时间和用户基本信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {

    /** 访问令牌（用于接口鉴权） */
    private String accessToken;

    /** 刷新令牌（用于 AccessToken 过期后换取新的 AccessToken） */
    private String refreshToken;

    /** AccessToken 过期时间（秒） */
    private Long expiresIn;

    /** 用户基本信息（脱敏后的用户资料） */
    private UserVO user;
}