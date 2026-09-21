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
     * 问答引擎：null=不修改；rag / style / auto
     * rag=纯RAG；style=RAG+风格提示词；auto=有风格提示词则叠加，否则纯RAG
     */
    @Pattern(regexp = "^(rag|style|auto)$", message = "问答引擎仅支持 rag/style/auto")
    private String chatEngine;

    /**
     * 风格提示词（风格蒸馏产物）：null=不修改；空字符串=清除；非空=设置
     */
    @Size(max = 4000, message = "风格提示词最长 4000 字符")
    private String stylePrompt;
}
