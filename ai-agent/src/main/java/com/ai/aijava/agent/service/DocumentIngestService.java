package com.ai.aijava.agent.service;

import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文档上传编排：校验 → 存盘 → 落库(UPLOADED) → 触发异步摄取
 * 异步摄取逻辑在 DocumentIngestWorker（拆分避免 @Async 同类自调用失效）
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestService {

    private final AgentProperties agentProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentIngestWorker ingestWorker;

    /**
     * 上传文档
     *
     * @return 文档 ID
     */
    public Long upload(Long kbId, MultipartFile file) {
        // 1. 校验知识库归属
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        // 2. 校验类型与大小
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || !agentProperties.getAllowedTypes().contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的文件类型，仅允许 " + String.join("/", agentProperties.getAllowedTypes()));
        }
        if (file.getSize() > agentProperties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "文件超过大小限制 " + agentProperties.getMaxFileSize().toMegabytes() + "MB");
        }
        // 3. 存盘：uploads/{kbId}/{uuid}.{ext}
        String filePath;
        try {
            Path dir = Paths.get(agentProperties.getUploadDir(), String.valueOf(kbId));
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext.toLowerCase());
            file.transferTo(target.toFile());
            filePath = target.toString();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败");
        }
        // 4. 落库（UPLOADED）；insert 自动提交后触发异步，Worker 必能查到记录
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .kbId(kbId)
                .fileName(file.getOriginalFilename())
                .fileType(ext.toLowerCase())
                .fileSize(file.getSize())
                .filePath(filePath)
                .status(DocStatus.UPLOADED.name())
                .build();
        knowledgeDocumentMapper.insert(doc);
        // 5. 异步摄取
        ingestWorker.ingest(doc.getId());
        return doc.getId();
    }
}
