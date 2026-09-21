package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测记录视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRecordVO {

    private Long id;

    private Long kbId;

    private String question;

    /** 纯 RAG 链路回答（不叠加风格提示词） */
    private String ragAnswer;

    /** RAG + 风格链路回答（system 叠加 style_prompt） */
    private String styleAnswer;

    private Integer ragScore;

    private Integer styleScore;

    private BigDecimal autoScoreRag;

    private BigDecimal autoScoreStyle;

    private String evaluatorComment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
