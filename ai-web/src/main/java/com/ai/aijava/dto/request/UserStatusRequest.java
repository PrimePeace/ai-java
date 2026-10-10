package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 封禁或解封。status 只接受 1（正常）或 2（封禁）。
 */
@Data
public class UserStatusRequest {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
