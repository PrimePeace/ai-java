package com.ai.aijava.agent.service;

import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.agent.pipeline.ChunkSplitter;
import com.ai.aijava.agent.pipeline.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 异步摄取 Worker（ingestExecutor 线程池）
 * 状态机：PROCESSING → COMPLETED / FAILED（FAILED 只能删除重传）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestWorker {

    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentParser documentParser;
    private final ChunkSplitter chunkSplitter;
    private final VectorStore vectorStore;

    /**
     * 摄取单个文档：解析 → 切分 → chunk 入库 → 向量入库
     */
    @Async("ingestExecutor")
    public void ingest(Long docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectOneById(docId);
        if (doc == null) {
            log.warn("摄取任务取消：文档不存在 docId={}", docId);
            return;
        }
        updateStatus(docId, DocStatus.PROCESSING, null);
        try {
            // 1. Tika 解析
            String text;
            try (FileInputStream fis = new FileInputStream(new File(doc.getFilePath()))) {
                text = documentParser.parse(fis);
            }
            // 2. 切分
            List<String> contents = chunkSplitter.split(text);
            // 3. chunk 批量入库（向量 Document id = chunk 自增 id）
            List<Document> vectorDocs = new ArrayList<>(contents.size());
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < contents.size(); i++) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .docId(doc.getId())
                        .kbId(doc.getKbId())
                        .chunkIndex(i)
                        .content(contents.get(i))
                        .createTime(now)
                        .build();
                documentChunkMapper.insert(chunk);
                vectorDocs.add(new Document(String.valueOf(chunk.getId()), contents.get(i),
                        Map.of("kbId", String.valueOf(doc.getKbId()),
                               "docId", String.valueOf(doc.getId()))));
            }
            // 4. 向量入库（内部自动调 EmbeddingModel 批量向量化）
            vectorStore.add(vectorDocs);
            updateStatus(docId, DocStatus.COMPLETED, null);
            log.info("文档摄取完成 docId={} kbId={} chunks={}", docId, doc.getKbId(), contents.size());
        } catch (Exception e) {
            // 批量 add 非严格原子可能残留部分向量，由查询侧按 docId 回查 status 过滤兜底（设计 4.2 ③）
            log.error("文档摄取失败 docId={}", docId, e);
            updateStatus(docId, DocStatus.FAILED, truncate(e.getMessage(), 512));
        }
    }

    private void updateStatus(Long docId, DocStatus status, String errorMessage) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(docId);
        update.setStatus(status.name());
        if (status == DocStatus.FAILED) {
            update.setErrorMessage(errorMessage);
        }
        knowledgeDocumentMapper.update(update);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
