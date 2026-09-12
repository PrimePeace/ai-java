package com.ai.aijava.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * ai-agent 可配参数（application.yml 的 agent.* 前缀）
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

    /** 对话携带历史轮数 */
    private int historyRounds = 10;

    /** RAG 系统提示词（空则用内置默认） */
    private String systemPrompt = "";

    /** 内置默认系统提示词 */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是知识库问答助手，仅依据参考资料回答问题；"
                    + "回答末尾不需要提及参考资料本身；"
                    + "当参考资料未覆盖提问内容时，明确说明未在知识库中找到直接依据。";

    /**
     * 获取生效的系统提示词
     */
    public String effectiveSystemPrompt() {
        return systemPrompt == null || systemPrompt.isBlank() ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
    }
}
