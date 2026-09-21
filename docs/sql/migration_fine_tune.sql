-- ==================== 模型微调模块 存量库迁移脚本 ====================
-- 适用于已部署的 ai_java 库：在已有表结构上增量执行
-- 新库直接执行 ai_java.sql 即可，无需执行本文件

ALTER TABLE `knowledge_base`
    ADD COLUMN `chat_engine` VARCHAR(16) NOT NULL DEFAULT 'rag' COMMENT '问答引擎（rag/ft/auto）' AFTER `prompt_template_id`;
ALTER TABLE `knowledge_base`
    ADD COLUMN `ft_model_id` VARCHAR(128) DEFAULT NULL COMMENT '绑定的微调模型ID（NULL=未绑定）' AFTER `chat_engine`;

CREATE TABLE IF NOT EXISTS `fine_tune_dataset` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '数据集ID',
    `kb_id`         BIGINT       NOT NULL COMMENT '所属知识库ID',
    `name`          VARCHAR(128) NOT NULL COMMENT '数据集名称',
    `description`   VARCHAR(256) DEFAULT '' COMMENT '数据集描述',
    `format`        VARCHAR(16)  NOT NULL DEFAULT 'chatml' COMMENT '数据格式（chatml）',
    `file_path`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'JSONL 文件存储路径',
    `sample_count`  INT          NOT NULL DEFAULT 0 COMMENT '样本数（Q&A 对数量）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'GENERATING' COMMENT '状态：GENERATING/READY/FAILED',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '生成失败原因',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微调数据集表';

CREATE TABLE IF NOT EXISTS `fine_tune_job` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `dataset_id`     BIGINT       NOT NULL COMMENT '关联数据集ID',
    `base_model`     VARCHAR(64)  NOT NULL COMMENT '基座模型（如 glm-4-flash）',
    `model_name`     VARCHAR(128) NOT NULL COMMENT '微调后模型名称',
    `zhipu_job_id`   VARCHAR(128) DEFAULT '' COMMENT '智谱 API 返回的任务ID',
    `zhipu_model_id` VARCHAR(128) DEFAULT '' COMMENT '智谱 API 返回的微调模型ID',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTING' COMMENT '状态：SUBMITTING/TRAINING/SUCCEEDED/FAILED/CANCELLED',
    `hyperparams`    VARCHAR(1024) DEFAULT NULL COMMENT '超参数 JSON（n_epochs, batch_size, learning_rate_multiplier）',
    `error_message`  VARCHAR(512) DEFAULT NULL COMMENT '训练失败原因',
    `progress`       TINYINT      DEFAULT 0 COMMENT '训练进度百分比（0-100，按状态映射估算）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_dataset_id` (`dataset_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微调任务表';

CREATE TABLE IF NOT EXISTS `evaluation_record` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评测ID',
    `kb_id`             BIGINT       NOT NULL COMMENT '所属知识库ID',
    `question`          TEXT         NOT NULL COMMENT '测试问题',
    `rag_answer`        TEXT         DEFAULT NULL COMMENT 'RAG 链路回答',
    `ft_answer`         TEXT         DEFAULT NULL COMMENT '微调模型回答',
    `rag_score`         TINYINT      DEFAULT NULL COMMENT 'RAG 回答人工评分（1-5）',
    `ft_score`          TINYINT      DEFAULT NULL COMMENT '微调模型人工评分（1-5）',
    `auto_score_rag`    DECIMAL(3,2) DEFAULT NULL COMMENT 'RAG 回答自动评测分（0.00-1.00）',
    `auto_score_ft`     DECIMAL(3,2) DEFAULT NULL COMMENT '微调模型自动评测分（0.00-1.00）',
    `evaluator_comment` VARCHAR(512) DEFAULT NULL COMMENT '自动评测评语',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测记录表';
