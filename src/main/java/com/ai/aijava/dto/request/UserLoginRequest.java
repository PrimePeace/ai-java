package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求
 * 前端登录接口（POST /user/login）的请求体。
 */
@Data
public class UserLoginRequest {

    /** 用户名（必填） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码（必填） */
    @NotBlank(message = "密码不能为空")
    private String password;
}