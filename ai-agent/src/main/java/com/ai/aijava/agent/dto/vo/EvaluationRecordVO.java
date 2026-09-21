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

    private String ragAnswer;

    private String ftAnswer;

    private Integer ragScore;

    private Integer ftScore;

    private BigDecimal autoScoreRag;

    private BigDecimal autoScoreFt;

    private String evaluatorComment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
