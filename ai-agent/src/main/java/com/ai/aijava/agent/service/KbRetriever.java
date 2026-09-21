package com.ai.aijava.agent.service;

import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库检索器：向量检索（过采样）→ 脏向量过滤 → 截取 topK → 引用/参考文本构建
 * 从 RagChatService 抽取，供 RAG 问答与评测链路复用
 */
@Component
@RequiredArgsConstructor
public class KbRetriever {

    private final VectorStore vectorStore;
    private final AgentProperties agentProperties;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    /**
     * 检索：过采样 topK*2 → 只保留 COMPLETED 文档的 hits → 截取前 topK
     * 无已完成文档时直接返回空，避免空库触发检索异常
     */
    public List<Document> retrieve(Long kbId, String question) {
        int topK = agentProperties.getTopK();
        long completedCount = knowledgeDocumentMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(KnowledgeDocument::getKbId).eq(kbId)
                        .and(KnowledgeDocument::getStatus).eq(DocStatus.COMPLETED.name()));
        List<Document> rawHits = List.of();
        if (completedCount > 0) {
            rawHits = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(topK * 2)
                            .filterExpression("kbId == '" + kbId + "'")
                            .build());
            String kbIdTag = String.valueOf(kbId);
            rawHits = rawHits.stream()
                    .filter(d -> kbIdTag.equals(String.valueOf(d.getMetadata().get("kbId"))))
                    .toList();
        }
        List<Document> hits = filterCompleted(rawHits);
        if (hits.size() > topK) {
            hits = new ArrayList<>(hits.subList(0, topK));
        }
        return hits;
    }

    /**
     * 过滤脏向量：只保留所属文档 status=COMPLETED 的 hits
     */
    private List<Document> filterCompleted(List<Document> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<Long> docIds = hits.stream()
                .map(d -> parseLongMeta(d, "docId"))
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (docIds.isEmpty()) {
            return List.of();
        }
        Set<String> completedDocIds = knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(KnowledgeDocument::getId)
                                .where(KnowledgeDocument::getId).in(docIds)
                                .and(KnowledgeDocument::getStatus).eq(DocStatus.COMPLETED.name()))
                .stream().map(d -> String.valueOf(d.getId())).collect(Collectors.toSet());
        return hits.stream()
                .filter(d -> completedDocIds.contains(String.valueOf(parseLongMeta(d, "docId"))))
                .toList();
    }

    /**
     * 构建引用列表（含 docName，内容截 150 字）
     */
    public List<CitationVO> buildCitations(List<Document> hits) {
        List<CitationVO> result = new ArrayList<>();
        List<Long> docIds = hits.stream()
                .map(d -> parseLongMeta(d, "docId"))
                .filter(id -> id > 0).distinct().toList();
        Map<Long, String> nameMap = Map.of();
        if (!docIds.isEmpty()) {
            nameMap = knowledgeDocumentMapper.selectListByQuery(
                            QueryWrapper.create()
                                    .select(KnowledgeDocument::getId, KnowledgeDocument::getFileName)
                                    .where(KnowledgeDocument::getId).in(docIds))
                    .stream().collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getFileName));
        }
        for (Document doc : hits) {
            String content = doc.getText();
            if (content != null && content.length() > 150) {
                content = content.substring(0, 150) + "...";
            }
            result.add(CitationVO.builder()
                    .chunkId(parseLongId(doc.getId()))
                    .docId(parseLongMeta(doc, "docId"))
                    .docName(nameMap.getOrDefault(parseLongMeta(doc, "docId"), "未知文档"))
                    .chunkIndex(parseIntMeta(doc, "chunkIndex"))
                    .content(content)
                    .score(doc.getScore() != null ? doc.getScore() : 0.0)
                    .build());
        }
        return result;
    }

    /**
     * 构建 Prompt 引用文本
     */
    public String buildReferences(List<Document> hits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(hits.get(i).getText()).append("\n\n");
        }
        return sb.toString();
    }

    private static long parseLongMeta(Document doc, String key) {
        Object v = doc.getMetadata().get(key);
        return parseLongId(v == null ? null : String.valueOf(v));
    }

    private static int parseIntMeta(Document doc, String key) {
        Object v = doc.getMetadata().get(key);
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLongId(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
