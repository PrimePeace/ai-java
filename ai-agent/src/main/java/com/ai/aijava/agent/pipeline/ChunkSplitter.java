package com.ai.aijava.agent.pipeline;

import com.ai.aijava.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 切分器：封装 Spring AI TokenTextSplitter，按 token 数切分
 */
@Component
@RequiredArgsConstructor
public class ChunkSplitter {

    private final AgentProperties agentProperties;

    /**
     * 将全文切分为片段（带默认重叠，保留语义连续性）
     *
     * @param text 文档全文
     * @return 切片列表（顺序即文档顺序）
     */
    public List<String> split(String text) {
        Document fullDoc = new Document(text);
        // Spring AI 2.0：5 参构造已移除，改用 Builder（包路径也迁移到 transformer.splitter）
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(agentProperties.getChunkSize()) // 切分 token 数
                .withMinChunkSizeChars(200)   // 低于此长度并入前片
                .withMinChunkLengthToEmbed(50) // 过短片段丢弃阈值，这里不丢
                .withMaxNumChunks(100)        // 安全上限，防超大文档切片爆炸
                .withKeepSeparator(true)      // 保留分隔符
                .build();
        List<Document> chunks = splitter.apply(List.of(fullDoc));
        return chunks.stream().map(Document::getText).toList();
    }
}
