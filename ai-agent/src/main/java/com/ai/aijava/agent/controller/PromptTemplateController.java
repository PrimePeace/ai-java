package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.request.PromptTemplateCreateRequest;
import com.ai.aijava.agent.dto.request.PromptTemplateUpdateRequest;
import com.ai.aijava.agent.dto.vo.PromptTemplateVO;
import com.ai.aijava.agent.service.PromptTemplateService;
import com.ai.aijava.annotation.AuditLog;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.annotation.RequirePermission;
import com.ai.aijava.auth.Permissions;
import com.ai.aijava.audit.AuditLogType;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提示词模板管理接口
 */
@Tag(name = "提示词模板")
@RequirePermission(Permissions.PROMPT_USE)
@RestController
@RequestMapping("/prompt")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @Operation(summary = "创建提示词模板")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_CREATE, module = "提示词模板", description = "创建提示词模板")
    @PostMapping("/create")
    public BaseResponse<PromptTemplateVO> create(@RequestBody @Valid PromptTemplateCreateRequest request) {
        return ResultUtils.success(promptTemplateService.create(request));
    }

    @Operation(summary = "我的提示词模板列表")
    @RequireLogin
    @GetMapping("/list")
    public BaseResponse<List<PromptTemplateVO>> list() {
        return ResultUtils.success(promptTemplateService.list());
    }

    @Operation(summary = "修改提示词模板")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_UPDATE, module = "提示词模板", description = "修改提示词模板")
    @PostMapping("/update")
    public BaseResponse<Void> update(@RequestBody @Valid PromptTemplateUpdateRequest request) {
        promptTemplateService.update(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除提示词模板（自动解绑知识库）")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_DELETE, module = "提示词模板", description = "删除提示词模板")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        promptTemplateService.delete(id);
        return ResultUtils.success(null);
    }
}
