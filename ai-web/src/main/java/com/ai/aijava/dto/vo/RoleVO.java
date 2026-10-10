package com.ai.aijava.dto.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 角色及已分配的菜单 ID。
 */
@Data
@Builder
public class RoleVO {

    private Long id;
    private String code;
    private String name;
    private Integer status;
    private String remark;
    private List<Long> menuIds;
}
