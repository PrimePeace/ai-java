package com.ai.aijava.agent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库实体，对应 knowledge_base 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("Knowledge_Base")
public class KnowledgeBase {

    /** 知识库 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 知识库名称 */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 绑定的提示词模板 ID（NULL=默认模板，弱引用，模板删除时自动解绑） */
    private Long promptTemplateId;

    /** 问答引擎：rag=纯 RAG 链路；ft=纯微调模型；auto=有微调模型走微调，否则回退 RAG */
    private String chatEngine;

    /** 绑定的微调模型 ID（NULL=未绑定，微调任务成功后自动回填） */
    private String ftModelId;

    /** 创建者用户 ID */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
