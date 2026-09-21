package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改知识库请求
 */
@Data
public class KnowledgeBaseUpdateRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long id;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 64, message = "知识库名称最长 64 字符")
    private String name;

    @Size(max = 256, message = "描述最长 256 字符")
    private String description;

    /**
     * 提示词模板绑定：null=不修改（旧客户端兼容）；0=解绑（默认模板）；>0=绑定该模板
     */
    private Long promptTemplateId;

    /**
     * 问答引擎：null=不修改；rag / ft / auto
     */
    @Pattern(regexp = "^(rag|ft|auto)$", message = "问答引擎仅支持 rag/ft/auto")
    private String chatEngine;

    /**
     * 微调模型绑定：null=不修改；空字符串=解绑；非空=绑定该微调模型
     */
    @Size(max = 128, message = "微调模型 ID 最长 128 字符")
    private String ftModelId;
}
