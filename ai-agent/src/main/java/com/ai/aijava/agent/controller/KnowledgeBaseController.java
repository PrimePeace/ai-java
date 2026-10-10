package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.request.KnowledgeBaseCreateRequest;
import com.ai.aijava.agent.dto.request.KnowledgeBaseUpdateRequest;
import com.ai.aijava.agent.dto.vo.KnowledgeBaseVO;
import com.ai.aijava.agent.service.KnowledgeBaseService;
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
 * 知识库管理接口
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "创建知识库")
    @RequireLogin
    @RequirePermission(Permissions.KB_EDIT)
    @AuditLog(type = AuditLogType.DATA_CREATE, module = "知识库管理", description = "创建知识库")
    @PostMapping("/create")
    public BaseResponse<KnowledgeBaseVO> create(@RequestBody @Valid KnowledgeBaseCreateRequest request) {
        return ResultUtils.success(knowledgeBaseService.create(request));
    }

    @Operation(summary = "我的知识库列表")
    @RequireLogin
    @RequirePermission(Permissions.KB_VIEW)
    @GetMapping("/list")
    public BaseResponse<List<KnowledgeBaseVO>> list() {
        return ResultUtils.success(knowledgeBaseService.listMine());
    }

    @Operation(summary = "修改知识库")
    @RequireLogin
    @RequirePermission(Permissions.KB_EDIT)
    @AuditLog(type = AuditLogType.DATA_UPDATE, module = "知识库管理", description = "修改知识库")
    @PostMapping("/update")
    public BaseResponse<Void> update(@RequestBody @Valid KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.update(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除知识库（级联清理文档/切片/向量/会话/文件）")
    @RequireLogin
    @RequirePermission(Permissions.KB_EDIT)
    @AuditLog(type = AuditLogType.DATA_DELETE, module = "知识库管理", description = "删除知识库")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ResultUtils.success(null);
    }
}
