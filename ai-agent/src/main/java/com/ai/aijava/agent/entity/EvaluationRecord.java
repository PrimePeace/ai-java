package com.ai.aijava.agent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测记录实体，对应 evaluation_record 表
 * 评测与数据集/任务解耦：按 KB 维度随时发起
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("evaluation_record")
public class EvaluationRecord {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long kbId;

    private String question;

    /** RAG 链路回答 */
    private String ragAnswer;

    /** 微调模型回答 */
    private String ftAnswer;

    /** 人工评分 1-5 */
    private Integer ragScore;

    private Integer ftScore;

    /** 自动评测分 0.00-1.00（LLM 裁判） */
    private BigDecimal autoScoreRag;

    private BigDecimal autoScoreFt;

    /** 自动评测评语 */
    private String evaluatorComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
