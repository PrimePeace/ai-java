package com.ai.aijava.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {

    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    private UserVO user;
}