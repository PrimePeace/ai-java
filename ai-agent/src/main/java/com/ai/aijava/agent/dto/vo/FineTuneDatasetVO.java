package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调数据集视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneDatasetVO {

    private Long id;

    private Long kbId;

    private String name;

    private String description;

    private String format;

    private Integer sampleCount;

    private String status;

    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
