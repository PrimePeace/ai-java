package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.vo.KnowledgeDocumentVO;
import com.ai.aijava.agent.service.DocumentIngestService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档管理接口
 */
@Tag(name = "知识库文档")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeDocController {

    private final DocumentIngestService documentIngestService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "上传文档（异步摄取，返回 docId）")
    @RequireLogin
    @RequirePermission(Permissions.KB_EDIT)
    @AuditLog(type = AuditLogType.DATA_CREATE, module = "知识库管理", description = "上传文档")
    @PostMapping("/{kbId}/document/upload")
    public BaseResponse<Long> upload(@PathVariable Long kbId, @RequestParam("file") MultipartFile file) {
        return ResultUtils.success(documentIngestService.upload(kbId, file));
    }

    @Operation(summary = "文档列表（含摄取状态，兼作进度轮询）")
    @RequireLogin
    @RequirePermission(Permissions.KB_VIEW)
    @GetMapping("/{kbId}/document/list")
    public BaseResponse<List<KnowledgeDocumentVO>> listDocuments(@PathVariable Long kbId) {
        return ResultUtils.success(knowledgeBaseService.listDocuments(kbId));
    }

    @Operation(summary = "删除文档（级联清理切片/向量/文件）")
    @RequireLogin
    @RequirePermission(Permissions.KB_EDIT)
    @AuditLog(type = AuditLogType.DATA_DELETE, module = "知识库管理", description = "删除文档")
    @DeleteMapping("/document/{docId}")
    public BaseResponse<Void> deleteDocument(@PathVariable Long docId) {
        knowledgeBaseService.deleteDocument(docId);
        return ResultUtils.success(null);
    }
}
