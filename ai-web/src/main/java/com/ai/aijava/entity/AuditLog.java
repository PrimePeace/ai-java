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
 * 审计日志实体类
 * 对应数据库 audit_log 表，记录敏感操作（登录、数据变更、权限变更等）。
 * 追加型日志表，只有 create_time，无 update_time。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("audit_log")
public class AuditLog {

    /** 日志 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 操作用户 ID（未登录时为 null） */
    private Long userId;

    /** 操作用户名（未登录时为 null） */
    private String username;

    /** 操作类型（AuditLogType 枚举名） */
    private String operationType;

    /** 业务模块 */
    private String operationModule;

    /** 操作描述 */
    private String description;

    /** 请求 URI */
    private String requestUri;

    /** 请求方法（GET/POST 等） */
    private String requestMethod;

    /** 请求参数（JSON，已脱敏截断） */
    private String requestParams;

    /** 客户端 IP */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    /** 操作结果（AuditLogResult 枚举名） */
    private String result;

    /** 异常信息（操作失败时） */
    private String errorMessage;

    /** 执行时长（毫秒） */
    private Integer executionTime;

    /** 被拦截方法签名 */
    private String methodSignature;

    /** 记录创建时间 */
    private LocalDateTime createTime;
}
