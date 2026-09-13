package com.ai.aijava.agent.service.mcp;

import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 工具：暴露知识库检索与列举给外部 AI 客户端（Claude Desktop / Cursor 等）
 * MCP 无用户态（不走 JWT），由 McpSecurityInterceptor 校验全局 token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeMcpTools {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorStore vectorStore;

    /**
     * 列出平台全部知识库
     */
    @McpTool(description = "列出平台全部知识库，返回知识库 ID、名称和描述")
    public String listKnowledgeBases() {
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(KnowledgeBase::getId, KnowledgeBase::getName, KnowledgeBase::getDescription)
                        .orderBy(KnowledgeBase::getCreateTime, false));
        StringBuilder sb = new StringBuilder("平台共有 " + kbs.size() + " 个知识库：\n\n");
        for (KnowledgeBase kb : kbs) {
            sb.append("- ").append(kb.getId()).append(" [").append(kb.getName()).append("] ")
                    .append(kb.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 搜索平台知识库（跨库检索）
     */
    @McpTool(description = "搜索平台知识库，返回最相关的原文片段与来源标注")
    public String searchKnowledge(
            @McpToolParam(description = "搜索关键词或问题", required = true) String query,
            @McpToolParam(description = "知识库 ID（可选，不提供则跨库检索）", required = false) Long kbId) {
        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(5);
        if (kbId != null) {
            builder.filterExpression("kbId == '" + kbId + "'");
        }
        List<Document> hits = vectorStore.similaritySearch(builder.build());
        if (hits.isEmpty()) {
            return "未检索到相关内容。";
        }
        StringBuilder sb = new StringBuilder("检索到 " + hits.size() + " 条相关内容：\n\n");
        for (int i = 0; i < hits.size(); i++) {
            Document doc = hits.get(i);
            String content = doc.getText();
            if (content != null && content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(content).append("\n");
            sb.append("   → 向量 ID: ").append(doc.getId()).append("\n\n");
        }
        return sb.toString();
    }
}
