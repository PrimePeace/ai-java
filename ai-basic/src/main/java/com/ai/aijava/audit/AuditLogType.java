package com.ai.aijava.audit;

/**
 * 审计日志操作类型枚举
 * 覆盖等保三级要求的敏感操作：登录、数据变更、权限变更、系统配置
 */
public enum AuditLogType {

    /** 用户登录 */
    LOGIN("用户登录"),

    /** 用户登出 */
    LOGOUT("用户登出"),

    /** 创建用户 */
    USER_CREATE("创建用户"),

    /** 修改用户 */
    USER_UPDATE("修改用户"),

    /** 删除用户 */
    USER_DELETE("删除用户"),

    /** 查询用户 */
    USER_QUERY("查询用户"),

    /** 新增数据 */
    DATA_CREATE("新增数据"),

    /** 修改数据 */
    DATA_UPDATE("修改数据"),

    /** 删除数据 */
    DATA_DELETE("删除数据"),

    /** 导出数据 */
    DATA_EXPORT("导出数据"),

    /** 权限变更（角色分配、授权等） */
    PERMISSION_CHANGE("权限变更"),

    /** 系统配置修改 */
    SYSTEM_CONFIG("系统配置修改"),

    /** 其他操作 */
    OTHER("其他操作");

    private final String label;

    AuditLogType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
