package com.ai.aijava.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * ai-agent 可配参数（application.yml 的 agent.* 前缀）
 *
 * systemPrompt / DEFAULT_SYSTEM_PROMPT 已移除：
 * 由 PromptTemplateService 的默认模板替代（Prompt 工程模块，见设计文档 2.3）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 上传文件存储根目录 */
    private String uploadDir = "./uploads";

    /** 允许上传的文件类型白名单（扩展名小写） */
    private List<String> allowedTypes = List.of("pdf", "docx", "md", "txt");

    /** 单文件大小上限 */
    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    /** 切分 token 数（TokenTextSplitter） */
    private int chunkSize = 800;

    /** 检索返回条数（过采样为 topK * 2，过滤后截取前 topK） */
    private int topK = 5;

    /** 对话携带历史轮数（DbChatMemory 窗口大小 = historyRounds * 2 条消息） */
    private int historyRounds = 10;

    /** 微调模块配置 */
    private FineTune fineTune = new FineTune();

    @Data
    public static class FineTune {

        /** 数据集 JSONL 存储目录 */
        private String uploadDir = "./uploads/fine_tune";

        /** 每个 chunk 默认生成的 Q&A 对数量 */
        private int qaPerChunk = 3;

        /** 风格蒸馏采样条数上限（从知识库 chunk 抽取的风格样本数） */
        private int styleSampleLimit = 20;

        /** 风格提示词最大长度（字符数，超出截断） */
        private int styleMaxLength = 2000;
    }
}
