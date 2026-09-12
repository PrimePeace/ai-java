package com.ai.aijava.controller;

import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import com.ai.aijava.dto.request.AuditLogQueryRequest;
import com.ai.aijava.entity.AuditLog;
import com.ai.aijava.service.AuditLogService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页查询审计日志")
    @RequireLogin
    @GetMapping("/list")
    public BaseResponse<Page<AuditLog>> listAuditLogs(@ParameterObject @Valid AuditLogQueryRequest request) {
        Page<AuditLog> page = auditLogService.pageQuery(request);
        return ResultUtils.success(page);
    }
}
