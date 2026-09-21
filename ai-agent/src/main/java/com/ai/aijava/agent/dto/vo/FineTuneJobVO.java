package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风格任务视图（原微调任务视图；官方微调已下线，baseModel/modelName/zhipu* 为历史遗留字段，新任务恒为 null）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneJobVO {

    private Long id;

    private Long datasetId;

    /** @deprecated 官方微调遗留字段，风格任务恒为 null */
    @Deprecated
    private String baseModel;

    /** @deprecated 官方微调遗留字段，风格任务恒为 null */
    @Deprecated
    private String modelName;

    /** @deprecated 官方微调遗留字段，风格任务恒为 null */
    @Deprecated
    private String zhipuJobId;

    /** @deprecated 官方微调遗留字段，风格任务恒为 null */
    @Deprecated
    private String zhipuModelId;

    private String status;

    private String errorMessage;

    private Integer progress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
