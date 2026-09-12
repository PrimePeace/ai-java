package com.ai.aijava.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志事件
 * 由 AuditLogAspect 发布，ai-web 侧 AuditLogService 异步监听并落库
 * Spring 4.2+ 事件对象无需继承 ApplicationEvent，POJO 即可
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEvent {

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

    /** 请求参数（JSON，已截断） */
    private String requestParams;

    /** 客户端 IP */
    private String ipAddress;

    /** User-Agent（已截断） */
    private String userAgent;

    /** 操作结果（AuditLogResult 枚举名） */
    private String result;

    /** 异常信息（操作失败时） */
    private String errorMessage;

    /** 执行时长（毫秒） */
    private Long executionTime;

    /** 被拦截方法签名 */
    private String methodSignature;

    /** 操作发生时间 */
    private LocalDateTime operateTime;
}
