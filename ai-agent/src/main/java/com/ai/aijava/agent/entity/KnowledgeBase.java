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

    /** 创建者用户 ID */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
