package com.ai.aijava.agent.enums;

/**
 * 微调数据集状态枚举（fine_tune_dataset.status）
 */
public enum DatasetStatus {

    /** 生成中（异步任务执行中） */
    GENERATING,

    /** 已就绪（JSONL 文件已生成，可用于微调） */
    READY,

    /** 生成失败（error_message 记录原因） */
    FAILED
}
