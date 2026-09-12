package com.ai.aijava.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询请求
 */
@Data
public class AuditLogQueryRequest {

    /** 当前页码（从 1 开始） */
    @Min(value = 1, message = "页码最小为 1")
    private Long current = 1L;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Long pageSize = 10L;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名（模糊匹配） */
    private String username;

    /** 操作类型（AuditLogType 枚举名） */
    private String operationType;

    /** 操作结果（AuditLogResult 枚举名） */
    private String result;

    /** 操作时间范围 - 起点 */
    private LocalDateTime startTime;

    /** 操作时间范围 - 终点 */
    private LocalDateTime endTime;
}
