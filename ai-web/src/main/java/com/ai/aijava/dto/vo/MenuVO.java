package com.ai.aijava.dto.vo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单节点。导航接口与管理接口共用。
 */
@Data
@Builder
public class MenuVO {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private String menuType;
    private String permission;
    private Integer visible;
    private String client;
    private Integer status;

    @Builder.Default
    private List<MenuVO> children = new ArrayList<>();
}
