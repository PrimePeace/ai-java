package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调任务视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneJobVO {

    private Long id;

    private Long datasetId;

    private String baseModel;

    private String modelName;

    private String zhipuJobId;

    private String zhipuModelId;

    private String status;

    private String errorMessage;

    private Integer progress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
