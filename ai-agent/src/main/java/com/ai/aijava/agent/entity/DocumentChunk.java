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
 * 文档切片实体，对应 document_chunk 表
 * 向量只存 Redis（Document id = chunk id），本表存原文用于引用溯源与索引重建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("document_chunk")
public class DocumentChunk {

    /** 切片 ID（主键，自增；同时作为 Redis 向量 Document id） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属文档 ID */
    private Long docId;

    /** 所属知识库 ID（冗余，删库清理向量时免 join） */
    private Long kbId;

    /** 切片序号（文档内从 0 递增） */
    private Integer chunkIndex;

    /** 切片原文 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
