package com.ai.aijava.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增或修改菜单。
 */
@Data
public class MenuSaveRequest {

    /** 为空表示新增 */
    private Long id;

    @NotNull(message = "上级菜单不能为空")
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称不能超过 64 位")
    private String name;

    @Size(max = 128, message = "路径不能超过 128 位")
    private String path;

    @Size(max = 128, message = "组件键不能超过 128 位")
    private String component;

    @Size(max = 64, message = "图标名不能超过 64 位")
    private String icon;

    @NotNull(message = "排序不能为空")
    private Integer sort;

    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    @Size(max = 64, message = "权限码不能超过 64 位")
    private String permission;

    @NotNull(message = "是否可见不能为空")
    private Integer visible;

    @NotBlank(message = "所属端不能为空")
    private String client;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
