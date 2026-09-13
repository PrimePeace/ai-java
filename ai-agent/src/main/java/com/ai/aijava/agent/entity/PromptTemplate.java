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
 * 提示词模板实体，对应 prompt_template 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("prompt_template")
public class PromptTemplate {

    /** 模板 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 系统提示词模板（变量 {kbName} {kbDescription}） */
    private String systemTemplate;

    /** 用户消息模板（变量 {question} {references} {referencesBlock}） */
    private String userTemplate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
