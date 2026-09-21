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

        /** 智谱微调 API Key（资源包专用 Key，见 agent.fine-tune.api-key） */
        private String apiKey = "";

        /** 智谱 API 基础地址（含 /api/paas/v4） */
        private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

        /** 默认微调基座模型（与已购 glm-4-flash 资源包对齐） */
        private String defaultBaseModel = "glm-4-flash";

        /** 微调任务状态轮询间隔（毫秒） */
        private long pollIntervalMs = 30000;
    }
}
