package com.ai.aijava.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 菜单与权限点，对应 menu 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("menu")
public class Menu {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 上级 ID，0 为根 */
    private Long parentId;

    private String name;

    private String path;

    /** 前端组件键，不是磁盘路径 */
    private String component;

    private String icon;

    private Integer sort;

    /** CATALOG / MENU / BUTTON */
    private String menuType;

    /** 接口权限码 */
    private String permission;

    /** 1 出现在菜单，0 只注册路由 */
    private Integer visible;

    /** WEB / APP / ALL */
    private String client;

    /** 1 启用，0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
