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
 * 用户实体类
 * 对应数据库 user 表，存储系统用户的基本信息、登录状态和安全数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user")
public class User {

    /** 用户 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** 登录密码（BCrypt 加密存储） */
    private String password;

    /** 用户昵称 */
    private String nickname;

    /** 邮箱地址 */
    private String email;

    /** 手机号码 */
    private String phone;

    /** 最后登录 IP 地址 */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginTime;

    /** 连续登录失败次数（用于账号锁定策略） */
    private Integer loginFailCount;

    /** 账号锁定时间 */
    private LocalDateTime lockTime;

    /**
     * 账号状态
     * 0 - 未激活
     * 1 - 正常
     * 2 - 已封禁
     * 3 - 已注销
     */
    private Integer status;

    /** 记录创建时间 */
    private LocalDateTime createTime;

    /** 记录更新时间 */
    private LocalDateTime updateTime;
}
