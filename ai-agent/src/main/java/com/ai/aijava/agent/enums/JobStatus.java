package com.ai.aijava.agent.enums;

/**
 * 风格任务状态枚举（fine_tune_job.status）
 * 状态机：SUBMITTING → SUCCEEDED / FAILED / CANCELLED（TRAINING 为历史遗留状态，新任务不再经过）
 */
public enum JobStatus {

    /** 蒸馏中（异步读取数据集并提炼风格规范） */
    SUBMITTING,

    /** @deprecated 历史遗留（原智谱侧 validating_files / queued / running），风格任务不再使用 */
    @Deprecated
    TRAINING,

    /** 蒸馏成功（style_prompt 已回填 knowledge_base） */
    SUCCEEDED,

    /** 训练失败 */
    FAILED,

    /** 已取消（用户主动取消） */
    CANCELLED
}
