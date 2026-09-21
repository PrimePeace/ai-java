package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库视图对象（含文档数实时统计）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseVO {

    private Long id;

    private String name;

    private String description;

    /** 绑定的提示词模板 ID（NULL=默认模板；前端下拉选中值） */
    private Long promptTemplateId;

    /** 问答引擎：rag / style / auto */
    private String chatEngine;

    /** 风格提示词（风格蒸馏产物，NULL=未生成） */
    private String stylePrompt;

    /** 文档数（GROUP BY 实时统计，不冗余字段） */
    private Long docCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
