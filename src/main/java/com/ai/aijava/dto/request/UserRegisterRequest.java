package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 * 前端注册接口（POST /user/register）的请求体，包含注册所需字段。
 * username 和 password 为必填项，nickname、email、phone 为选填项。
 */
@Data
public class UserRegisterRequest {

    /** 用户名（必填，长度 1-32 字符） */
    @NotBlank(message = "用户名不为空")
    @Size(min = 1,max = 32,message = "用户名长度必须在 4-32 位之间")
    private String username;

    /** 登录密码（必填，最少 8 字符） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能少于 8 位")
    private String password;

    /** 用户昵称（选填，最多 64 字符） */
    @Size(max = 64, message = "昵称长度不能超过 64 位")
    private String nickname;

    /** 邮箱（选填） */
    private String email;

    /** 手机号（选填） */
    private String phone;
}
