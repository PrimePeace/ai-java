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
 * 角色，对应 role 表。内置 USER、ADMIN 不可删除、不可改编码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("role")
public class Role {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 角色编码 */
    private String code;

    /** 显示名称 */
    private String name;

    /** 1 启用，0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
