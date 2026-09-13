package com.ai.aijava.agent.service;

import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import cn.hutool.json.JSONUtil;
import com.ai.aijava.exception.BusinessException;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 流式问答（核心）
 * 检索 → 过滤 → Prompt 拼装 → ChatModel 流式 → SSE 推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final AgentProperties agentProperties;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ChatSessionService chatSessionService;

    /**
     * 会话提问 → SSE Flux（4.2 事件协议）
     */
    public Flux<ServerSentEvent<String>> chat(Long sessionId, String question) {
        try {
            return doChat(sessionId, question);
        } catch (BusinessException e) {
            return errorFlux(e.getMessage());
        } catch (Exception e) {
            log.error("RAG 问答准备阶段异常", e);
            return errorFlux("问答失败，请稍后重试");
        }
    }

    private Flux<ServerSentEvent<String>> doChat(Long sessionId, String question) {
        // 归属校验（取会话 → 取 kbId）
        var session = chatSessionService.getOwnedSession(sessionId);
        Long kbId = session.getKbId();
        int topK = agentProperties.getTopK();
        int overSampleK = topK * 2; // 过采样（设计 4.2 决策 #5）

        // 检索（过采样）。无已完成文档时跳过向量库，避免空库触发检索异常
        long completedCount = knowledgeDocumentMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(KnowledgeDocument::getKbId).eq(kbId)
                        .and(KnowledgeDocument::getStatus).eq(DocStatus.COMPLETED.name()));
        List<Document> rawHits = List.of();
        if (completedCount > 0) {
            rawHits = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(overSampleK)
                            .filterExpression("kbId == '" + kbId + "'")
                            .build());
            String kbIdTag = String.valueOf(kbId);
            rawHits = rawHits.stream()
                    .filter(d -> kbIdTag.equals(String.valueOf(d.getMetadata().get("kbId"))))
                    .toList();
        }

        // 过滤脏向量（只保留所属文档 status=COMPLETED 的 hits），再截前 topK
        List<Document> hits = filterCompleted(rawHits);
        if (hits.size() > topK) {
            hits = new ArrayList<>(hits.subList(0, topK));
        }

        // 构建 citations（含 docName）
        List<CitationVO> citations = buildCitations(hits);

        // 历史（丢弃末尾孤立 user）
        List<Message> history = chatSessionService.getHistory(sessionId, agentProperties.getHistoryRounds());

        // 拼装 Prompt：system → history → 当前 user（2.0 只有 Prompt(List<Message>) / Prompt(List, ChatOptions)）
        String refText = buildReferences(hits);
        String userText = (refText.isBlank() ? "" : "参考资料：\n" + refText + "\n\n")
                + "问题：" + question;
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(agentProperties.effectiveSystemPrompt()));
        messages.addAll(history);
        messages.add(new UserMessage(userText));
        Prompt prompt = new Prompt(messages);

        // user 消息落库
        chatSessionService.saveUserMessage(sessionId, question);
        chatSessionService.updateTitleIfNeeded(sessionId, question);

        // 流式生成 + 装配 SSE
        return assembleFlux(chatModel.stream(prompt), sessionId, citations);
    }

    private Flux<ServerSentEvent<String>> errorFlux(String msg) {
        String safe = msg == null ? "未知错误" : msg.replaceAll("[\\r\\n]", "");
        return Flux.just(ServerSentEvent.<String>builder()
                .event("error")
                .data(safe)
                .build());
    }

    /**
     * 将 ChatModel Flux 装配为 SSE 事件流（message / citations / end / error）
     * 设计 4.2 决策 #7：onErrorResume 兜底异常转 error 事件
     */
    private Flux<ServerSentEvent<String>> assembleFlux(Flux<ChatResponse> responseFlux, Long sessionId,
                                                       List<CitationVO> citations) {
        StringBuilder aggregated = new StringBuilder();
        return responseFlux
                .doOnNext(chunk -> {
                    String content = chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getText() : "";
                    if (content != null && !content.isBlank()) {
                        aggregated.append(content);
                    }
                })
                .mapNotNull(chunk -> {
                    String content = chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getText() : "";
                    if (content == null || content.isBlank()) {
                        return null;
                    }
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(content)
                            .build();
                })
                .doOnComplete(() -> {
                    try {
                        String citationsJson = JSONUtil.toJsonStr(citations);
                        chatSessionService.saveAssistantMessage(sessionId, aggregated.toString(), citationsJson);
                        chatSessionService.refreshSessionActiveTime(sessionId);
                    } catch (Exception e) {
                        log.error("保存助手消息失败", e);
                    }
                })
                .concatWith(
                        Mono.just(ServerSentEvent.<String>builder()
                                .event("citations")
                                .data(JSONUtil.toJsonStr(citations))
                                .build()))
                .concatWith(Mono.just(ServerSentEvent.<String>builder().event("end").build()))
                .onErrorResume(e -> {
                    log.error("RAG 问答流式异常", e);
                    // error 事件
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("回答失败：" + (e.getMessage() != null ? e.getMessage().replaceAll("[\\r\\n]", "") : "未知错误"))
                            .build());
                });
    }

    /**
     * 过滤脏向量：只保留所属文档 status=COMPLETED 的 hits（设计 4.2 ③）
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
        java.util.Set<String> completedDocIds = knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(KnowledgeDocument::getId)
                                .where(KnowledgeDocument::getId).in(docIds)
                                .and(KnowledgeDocument::getStatus).eq(DocStatus.COMPLETED.name()))
                .stream().map(d -> String.valueOf(d.getId())).collect(java.util.stream.Collectors.toSet());
        return hits.stream()
                .filter(d -> completedDocIds.contains(String.valueOf(parseLongMeta(d, "docId"))))
                .toList();
    }

    /**
     * 构建引用列表（含 docName）
     */
    private List<CitationVO> buildCitations(List<Document> hits) {
        List<CitationVO> result = new ArrayList<>();
        // 按 docId 批量查 docName
        List<Long> docIds = hits.stream()
                .map(d -> parseLongMeta(d, "docId"))
                .filter(id -> id > 0).distinct().toList();
        Map<Long, String> nameMap = Map.of();
        if (!docIds.isEmpty()) {
            nameMap = knowledgeDocumentMapper.selectListByQuery(
                            QueryWrapper.create()
                                    .select(KnowledgeDocument::getId, KnowledgeDocument::getFileName)
                                    .where(KnowledgeDocument::getId).in(docIds))
                    .stream().collect(java.util.stream.Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getFileName));
        }
        for (Document doc : hits) {
            Long chunkId = parseLongId(doc.getId());
            Long docId = parseLongMeta(doc, "docId");
            Integer chunkIndex = parseIntMeta(doc, "chunkIndex");
            Double score = doc.getScore() != null ? doc.getScore() : 0.0;
            String content = doc.getText();
            // 节选前 150 字
            if (content != null && content.length() > 150) {
                content = content.substring(0, 150) + "...";
            }
            result.add(CitationVO.builder()
                    .chunkId(chunkId)
                    .docId(docId)
                    .docName(nameMap.getOrDefault(docId, "未知文档"))
                    .chunkIndex(chunkIndex)
                    .content(content)
                    .score(score)
                    .build());
        }
        return result;
    }

    /**
     * 构建 Prompt 引用文本
     */
    private String buildReferences(List<Document> hits) {
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
