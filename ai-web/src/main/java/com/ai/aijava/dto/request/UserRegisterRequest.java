package com.ai.aijava.dto.request;

import com.ai.aijava.auth.CredentialRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 * 前端注册接口（POST /user/register）的请求体，包含注册所需字段。
 * username 和 password 为必填项，nickname、email、phone 为选填项。
 */
@Data
public class UserRegisterRequest {

    /** 用户名（4-32 位字母、数字、下划线） */
    @NotBlank(message = "用户名不为空")
    @Pattern(regexp = CredentialRules.USERNAME_PATTERN, message = "用户名须为 4-32 位字母、数字或下划线")
    private String username;

    /** 登录密码（至少 8 位，且同时包含字母和数字） */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = CredentialRules.PASSWORD_PATTERN, message = "密码至少 8 位，且同时包含字母和数字")
    private String password;

    /** 用户昵称（选填，最多 64 字符） */
    @Size(max = 64, message = "昵称长度不能超过 64 位")
    private String nickname;

    /** 邮箱（选填） */
    private String email;

    /** 手机号（选填） */
    private String phone;
}
