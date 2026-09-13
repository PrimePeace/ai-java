package com.ai.aijava.agent.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用溯源条目（内容快照，不反查已删文档）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationVO {

    /** 切片 ID */
    private Long chunkId;

    /** 文档 ID */
    private Long docId;

    /** 文档名 */
    private String docName;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 片段原文节选 */
    private String content;

    /** 相似度得分 */
    private Double score;
}
