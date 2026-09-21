package com.ai.aijava.agent.enums;

/**
 * 微调任务状态枚举（fine_tune_job.status）
 * 状态机：SUBMITTING → TRAINING → SUCCEEDED / FAILED / CANCELLED
 */
public enum JobStatus {

    /** 提交中（正在上传数据集 / 创建智谱任务） */
    SUBMITTING,

    /** 训练中（智谱侧 validating_files / queued / running） */
    TRAINING,

    /** 训练成功（zhipu_model_id 已回填） */
    SUCCEEDED,

    /** 训练失败 */
    FAILED,

    /** 已取消（用户主动取消） */
    CANCELLED
}
