package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 调整用户角色。
 */
@Data
public class UserAssignRoleRequest {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;
}
