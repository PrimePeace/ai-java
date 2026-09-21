package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建风格任务请求（风格蒸馏：从数据集样本提炼风格规范 System Prompt）
 */
@Data
public class CreateFineTuneJobRequest {

    @NotNull(message = "数据集 ID 不能为空")
    private Long datasetId;

    /** 抽样条数（可选，缺省取配置 agent.fine-tune.style-sample-limit） */
    @Min(value = 1, message = "抽样条数最小为 1")
    @Max(value = 200, message = "抽样条数最大为 200")
    private Integer sampleLimit;
}
