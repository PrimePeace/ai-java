package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建微调任务请求（超参数对齐智谱 API：n_epochs / batch_size / learning_rate_multiplier）
 */
@Data
public class CreateFineTuneJobRequest {

    @NotNull(message = "数据集 ID 不能为空")
    private Long datasetId;

    @NotBlank(message = "基座模型不能为空")
    @Size(max = 64, message = "基座模型名最长 64 字符")
    private String baseModel;

    /**
     * 智谱模型编码后缀（suffix）：长度必须 1-8，仅字母数字下划线连字符
     * 最终微调模型 ID ≈ 基座模型名-suffix-日期
     */
    @NotBlank(message = "模型后缀不能为空")
    @Size(min = 1, max = 8, message = "模型后缀长度必须为 1-8 位（智谱 API 约束）")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "模型后缀仅支持字母、数字、下划线和连字符")
    private String modelName;

    /** 学习率倍数（智谱 learning_rate_multiplier），默认 1.0 */
    @DecimalMin(value = "0.01", message = "学习率倍数最小 0.01")
    @DecimalMax(value = "10.0", message = "学习率倍数最大 10.0")
    private Double learningRateMultiplier;

    /** 训练轮数（智谱 n_epochs），默认 3 */
    @Min(value = 1, message = "训练轮数最小为 1")
    @Max(value = 10, message = "训练轮数最大为 10")
    private Integer epochs;

    /** 批大小（智谱 batch_size），默认 4 */
    @Min(value = 1, message = "批大小最小为 1")
    @Max(value = 64, message = "批大小最大为 64")
    private Integer batchSize;
}
