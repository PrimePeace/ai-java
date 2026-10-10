package com.ai.aijava.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色与菜单关联，对应 role_menu 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("role_menu")
public class RoleMenu {

    @Id(keyType = KeyType.None)
    private Long roleId;

    @Id(keyType = KeyType.None)
    private Long menuId;
}
