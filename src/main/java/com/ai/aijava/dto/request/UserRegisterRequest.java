package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterRequest {

    @NotBlank(message = "用户名不为空")
    @Size(min = 1,max = 32,message = "用户名长度必须在 4-32 位之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能少于 8 位")
    private String password;

    @Size(max = 64, message = "昵称长度不能超过 64 位")
    private String nickname;

    private String email;

    private String phone;
}
