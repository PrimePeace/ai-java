package com.ai.aijava.audit;

/**
 * 审计日志操作结果枚举
 */
public enum AuditLogResult {

    /** 操作成功 */
    SUCCESS("成功"),

    /** 操作失败（抛出异常或业务拒绝） */
    FAILURE("失败"),

    /** 部分成功（批量操作场景，由业务代码手动发布事件时使用） */
    PARTIAL("部分成功");

    private final String label;

    AuditLogResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
