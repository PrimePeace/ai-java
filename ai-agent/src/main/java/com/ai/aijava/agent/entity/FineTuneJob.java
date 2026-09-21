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
 * 微调任务实体，对应 fine_tune_job 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("fine_tune_job")
public class FineTuneJob {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 关联数据集 ID */
    private Long datasetId;

    /** 基座模型（如 glm-4-flash） */
    private String baseModel;

    /** 微调后模型名称 */
    private String modelName;

    /** 智谱 API 任务 ID */
    private String zhipuJobId;

    /** 智谱 API 微调模型 ID */
    private String zhipuModelId;

    /** SUBMITTING / TRAINING / SUCCEEDED / FAILED / CANCELLED */
    private String status;

    /** 超参数 JSON（n_epochs / batch_size / learning_rate_multiplier） */
    private String hyperparams;

    private String errorMessage;

    /** 训练进度 0-100（智谱无进度字段，按状态映射估算） */
    private Integer progress;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
