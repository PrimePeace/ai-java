package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 覆盖保存角色拥有的菜单。
 */
@Data
public class RoleMenuAssignRequest {

    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    @NotNull(message = "菜单列表不能为空")
    private List<Long> menuIds;
}
