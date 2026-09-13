package com.ai.aijava.agent.service;

import com.ai.aijava.agent.dto.request.KnowledgeBaseCreateRequest;
import com.ai.aijava.agent.dto.request.KnowledgeBaseUpdateRequest;
import com.ai.aijava.agent.dto.vo.KnowledgeBaseVO;
import com.ai.aijava.agent.dto.vo.KnowledgeDocumentVO;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.entity.ChatSession;
import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.agent.mapper.ChatSessionMapper;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库服务：CRUD + 级联删除（设计 3.4：同步删除 + 顺序约束 + 幂等）
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final VectorStore vectorStore;

    /** 自注入代理：保证 @Transactional 方法经代理生效 */
    @Resource
    @Lazy
    private KnowledgeBaseService self;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeDocumentMapper knowledgeDocumentMapper,
                                DocumentChunkMapper documentChunkMapper,
                                ChatSessionMapper chatSessionMapper,
                                ChatMessageMapper chatMessageMapper,
                                VectorStore vectorStore) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 创建知识库
     */
    public KnowledgeBaseVO create(KnowledgeBaseCreateRequest request) {
        // 防御：用户上下文缺失时快速失败，避免 DB 约束异常变成 500
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase kb = KnowledgeBase.builder()
                .name(request.getName())
                .description(request.getDescription())
                .userId(userId)
                .createTime(now)
                .updateTime(now)
                .build();
        knowledgeBaseMapper.insert(kb);
        return KnowledgeBaseVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .docCount(0L)
                .createTime(kb.getCreateTime())
                .updateTime(kb.getUpdateTime())
                .build();
    }

    /**
     * 我的知识库列表（含 docCount 实时统计）
     */
    public List<KnowledgeBaseVO> listMine() {
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectListByQuery(
                QueryWrapper.create().where(KnowledgeBase::getUserId).eq(UserContext.getUserId())
                        .orderBy(KnowledgeBase::getCreateTime, false));
        if (kbs.isEmpty()) {
            return List.of();
        }
        // 一次 GROUP BY 查全部 docCount（不冗余统计字段，设计 3.3 #7）
        List<Long> kbIds = kbs.stream().map(KnowledgeBase::getId).toList();
        Map<Long, Long> countMap = knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(KnowledgeDocument::getKbId)
                                .where(KnowledgeDocument::getKbId).in(kbIds))
                .stream().collect(Collectors.groupingBy(KnowledgeDocument::getKbId, Collectors.counting()));
        return kbs.stream().map(kb -> KnowledgeBaseVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .docCount(countMap.getOrDefault(kb.getId(), 0L))
                .createTime(kb.getCreateTime())
                .updateTime(kb.getUpdateTime())
                .build()).toList();
    }

    /**
     * 修改知识库（名称/描述）
     */
    public void update(KnowledgeBaseUpdateRequest request) {
        KnowledgeBase kb = getOwnedKb(request.getId());
        KnowledgeBase update = new KnowledgeBase();
        update.setId(kb.getId());
        update.setName(request.getName());
        update.setDescription(request.getDescription());
        knowledgeBaseMapper.update(update);
    }

    /**
     * 删除知识库——级联清理全链（设计 3.4）：
     * ① 缓存 file_path + chunkId → ② 删 Redis 向量（失败中止）
     * → ③ 删 MySQL（chat_message → chat_session → chunk → doc → kb，单事务）
     * → ④ 删磁盘文件（失败仅记日志）
     */
    public void delete(Long kbId) {
        KnowledgeBase kb = getOwnedKb(kbId);
        // ① 前置缓存（file_path + chunkId）
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectListByQuery(
                QueryWrapper.create().where(KnowledgeDocument::getKbId).eq(kbId));
        List<String> filePaths = docs.stream().map(KnowledgeDocument::getFilePath)
                .filter(p -> p != null && !p.isBlank()).toList();
        List<Long> chunkIds = documentChunkMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(DocumentChunk::getId)
                                .where(DocumentChunk::getKbId).eq(kbId))
                .stream().map(DocumentChunk::getId).toList();
        // ② 删向量（按 id 分批，失败中止不删 MySQL）
        deleteVectors(chunkIds);
        // ③ 删 MySQL 记录（经代理调用，事务生效）
        self.deleteRecordsTransaction(kbId);
        // ④ 删磁盘文件（失败不影响）
        filePaths.forEach(this::deleteFileQuietly);
        log.info("知识库已删除 kbId={} name={} docs={} chunks={}", kbId, kb.getName(), docs.size(), chunkIds.size());
    }

    /**
     * 删除文档——级联（缓存 → 删向量 → 删记录 → 删文件）
     * ②③ 步骤事务保证原子性：②删向量（外部依赖失败即中止）→ ③删 MySQL（单事务）
     */
    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectOneById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        getOwnedKb(doc.getKbId()); // 归属校验（不拥有则抛异常）
        List<Long> chunkIds = documentChunkMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(DocumentChunk::getId)
                                .where(DocumentChunk::getDocId).eq(docId))
                .stream().map(DocumentChunk::getId).toList();
        deleteVectors(chunkIds);
        // ③ 删 MySQL 记录（经代理调用，事务生效）
        self.deleteDocumentRecordsTransaction(docId);
        // ④ 删磁盘文件（失败不影响）
        deleteFileQuietly(doc.getFilePath());
        log.info("文档已删除 docId={} kbId={} chunks={}", docId, doc.getKbId(), chunkIds.size());
    }

    /**
     * 文档列表（按知识库，含状态；归属校验）
     */
    public List<KnowledgeDocumentVO> listDocuments(Long kbId) {
        getOwnedKb(kbId);
        return knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(KnowledgeDocument::getKbId).eq(kbId)
                                .orderBy(KnowledgeDocument::getCreateTime, false))
                .stream().map(doc -> KnowledgeDocumentVO.builder()
                        .id(doc.getId())
                        .kbId(doc.getKbId())
                        .fileName(doc.getFileName())
                        .fileType(doc.getFileType())
                        .fileSize(doc.getFileSize())
                        .status(doc.getStatus())
                        .errorMessage(doc.getErrorMessage())
                        .createTime(doc.getCreateTime())
                        .updateTime(doc.getUpdateTime())
                        .build()).toList();
    }

    /**
     * 校验知识库归属当前用户，返回实体（5.5 #4：归属校验在 Service 层）
     */
    public KnowledgeBase getOwnedKb(Long kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        return kb;
    }

    /**
     * 按 id 分批删向量（≤500/批，幂等：删不存在的 id 是 no-op）
     */
    private void deleteVectors(List<Long> chunkIds) {
        for (int i = 0; i < chunkIds.size(); i += 500) {
            List<String> batch = chunkIds.subList(i, Math.min(i + 500, chunkIds.size()))
                    .stream().map(String::valueOf).toList();
            vectorStore.delete(batch);
        }
    }

    /**
     * 删 MySQL 全链（单事务；顺序按依赖：message → session → chunk → doc → kb）
     * 必须经代理调用才生效（见 Step 2 自注入代理修正）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecordsTransaction(Long kbId) {
        List<Long> sessionIds = chatSessionMapper.selectListByQuery(
                        QueryWrapper.create().select(ChatSession::getId)
                                .where(ChatSession::getKbId).eq(kbId))
                .stream().map(ChatSession::getId).toList();
        if (!sessionIds.isEmpty()) {
            chatMessageMapper.deleteByQuery(QueryWrapper.create()
                    .where(ChatMessage::getSessionId).in(sessionIds));
            chatSessionMapper.deleteByQuery(QueryWrapper.create()
                    .where(ChatSession::getKbId).eq(kbId));
        }
        documentChunkMapper.deleteByQuery(QueryWrapper.create()
                .where(DocumentChunk::getKbId).eq(kbId));
        knowledgeDocumentMapper.deleteByQuery(QueryWrapper.create()
                .where(KnowledgeDocument::getKbId).eq(kbId));
        knowledgeBaseMapper.deleteById(kbId);
    }

    /**
     * 删除单个文档的 MySQL 记录（单事务；chunk → doc）
     * 必须经代理调用才生效（见 Step 2 自注入代理修正）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocumentRecordsTransaction(Long docId) {
        documentChunkMapper.deleteByQuery(QueryWrapper.create()
                .where(DocumentChunk::getDocId).eq(docId));
        knowledgeDocumentMapper.deleteById(docId);
    }

    /**
     * 删磁盘文件（不存在忽略，失败仅记日志）
     */
    private void deleteFileQuietly(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            log.error("文件删除失败（不影响检索，可人工清理）: {}", filePath);
        }
    }
}
