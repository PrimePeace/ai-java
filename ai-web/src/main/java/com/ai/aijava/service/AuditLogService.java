package com.ai.aijava.service;

import com.ai.aijava.audit.AuditLogEvent;
import com.ai.aijava.dto.request.AuditLogQueryRequest;
import com.ai.aijava.entity.AuditLog;
import com.ai.aijava.mapper.AuditLogMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 审计日志服务
 * 1. 异步监听 AuditLogEvent 并落库（写入失败仅记录错误日志，不影响主业务）
 * 2. 提供审计日志分页查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 异步监听审计事件并落库
     * 线程池：auditLogExecutor（见 AsyncConfig）
     */
    @Async("auditLogExecutor")
    @EventListener
    public void onAuditLogEvent(AuditLogEvent event) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(event.getUserId())
                    .username(event.getUsername())
                    .operationType(event.getOperationType())
                    .operationModule(event.getOperationModule())
                    .description(event.getDescription())
                    .requestUri(event.getRequestUri())
                    .requestMethod(event.getRequestMethod())
                    .requestParams(event.getRequestParams())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .result(event.getResult())
                    .errorMessage(event.getErrorMessage())
                    .executionTime(event.getExecutionTime() != null ? event.getExecutionTime().intValue() : null)
                    .methodSignature(event.getMethodSignature())
                    .createTime(LocalDateTime.now())
                    .build();
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 落库失败不抛出，防止干扰主业务；审计文件日志仍可作为兜底
            log.error("审计日志入库失败: type={}, user={}, uri={}",
                    event.getOperationType(), event.getUsername(), event.getRequestUri(), e);
        }
    }

    /**
     * 分页查询审计日志
     *
     * @param request 查询条件（用户/类型/结果/时间范围）
     * @return 分页结果
     */
    public Page<AuditLog> pageQuery(AuditLogQueryRequest request) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(AuditLog::getUserId).eq(request.getUserId(), request.getUserId() != null)
                .and(AuditLog::getUsername).like(request.getUsername(), StringUtils.hasText(request.getUsername()))
                .and(AuditLog::getOperationType).eq(request.getOperationType(), StringUtils.hasText(request.getOperationType()))
                .and(AuditLog::getResult).eq(request.getResult(), StringUtils.hasText(request.getResult()))
                .and(AuditLog::getCreateTime).ge(request.getStartTime(), request.getStartTime() != null)
                .and(AuditLog::getCreateTime).le(request.getEndTime(), request.getEndTime() != null)
                .orderBy(AuditLog::getCreateTime, false);
        return auditLogMapper.paginate(request.getCurrent(), request.getPageSize(), queryWrapper);
    }
}
