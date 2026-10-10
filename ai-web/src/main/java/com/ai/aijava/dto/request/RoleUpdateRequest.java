package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改角色名称和备注。编码不允许改。
 */
@Data
public class RoleUpdateRequest {

    @NotNull(message = "角色 ID 不能为空")
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称不能超过 64 位")
    private String name;

    @Size(max = 256, message = "备注不能超过 256 位")
    private String remark;
}
