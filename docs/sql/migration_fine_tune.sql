-- ==================== 模型微调模块 存量库迁移脚本 ====================
-- 适用于已部署的 ai_java 库：在已有表结构上增量执行
-- 新库直接执行 ai_java.sql 即可，无需执行本文件
--
-- 说明：
--   1. 本脚本可重复执行（幂等）：加列前查 information_schema 判存在，MODIFY/UPDATE 语句本身幂等
--   2. 本脚本使用 DELIMITER 定义存储过程，请用 mysql CLI / Navicat / DataGrip 等客户端整文件执行
--   3. v2 变更：风格蒸馏（style）替换官方微调（ft）
--      - knowledge_base 新增 style_prompt 列，chat_engine 取值语义更新为 rag/style/auto（历史值 ft 按 style 兼容）
--      - knowledge_base.ft_model_id 停用（代码已移除映射，仅保留列兼容存量数据）
--      - fine_tune_job.zhipu_job_id / zhipu_model_id 停用并改为可空，仅保留历史数据
--      - evaluation_record 的 ft_answer/ft_score/auto_score_ft 重命名为 style_answer/style_score/auto_score_style
--        （评测对比改为「纯 RAG」vs「RAG+风格」，历史数据随列重命名保留）

-- ---------- 幂等工具存储过程：列不存在时才执行 ALTER ----------
DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `sp_add_column_if_missing`(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = p_table
           AND COLUMN_NAME  = p_column
    ) THEN
        SET @migration_ddl := p_ddl;
        PREPARE stmt FROM @migration_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------- 幂等工具存储过程：旧列存在且新列不存在时才执行 RENAME ----------
DROP PROCEDURE IF EXISTS `sp_rename_column_if_present`;
DELIMITER $$
CREATE PROCEDURE `sp_rename_column_if_present`(
    IN p_table  VARCHAR(64),
    IN p_old    VARCHAR(64),
    IN p_new    VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = p_table
           AND COLUMN_NAME  = p_old
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = p_table
           AND COLUMN_NAME  = p_new
    ) THEN
        SET @migration_ddl := p_ddl;
        PREPARE stmt FROM @migration_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------- knowledge_base：问答引擎 / 微调模型绑定 / 风格提示词 ----------
CALL `sp_add_column_if_missing`('knowledge_base', 'chat_engine',
    'ALTER TABLE `knowledge_base` ADD COLUMN `chat_engine` VARCHAR(16) NOT NULL DEFAULT ''rag'' COMMENT ''问答引擎（rag/style/auto）'' AFTER `prompt_template_id`');

CALL `sp_add_column_if_missing`('knowledge_base', 'ft_model_id',
    'ALTER TABLE `knowledge_base` ADD COLUMN `ft_model_id` VARCHAR(128) DEFAULT NULL COMMENT ''绑定的微调模型ID（NULL=未绑定）'' AFTER `chat_engine`');

CALL `sp_add_column_if_missing`('knowledge_base', 'style_prompt',
    'ALTER TABLE `knowledge_base` ADD COLUMN `style_prompt` TEXT DEFAULT NULL COMMENT ''风格提示词（风格蒸馏产物，NULL=未生成；style/auto 引擎时拼入系统提示词）'' AFTER `ft_model_id`');

-- 字段注释对齐 v2 语义（MODIFY 幂等，可重复执行；与 ai_java.sql 新库 DDL 保持一致）
ALTER TABLE `knowledge_base`
    MODIFY COLUMN `chat_engine` VARCHAR(16) NOT NULL DEFAULT 'rag'
        COMMENT '问答引擎（rag/style/auto：rag=纯RAG；style=RAG+风格提示词；auto=有风格提示词则叠加，否则纯RAG）',
    MODIFY COLUMN `ft_model_id` VARCHAR(128) DEFAULT NULL
        COMMENT '【已停用】官方微调模型ID（风格蒸馏替代官方微调；代码已移除映射，仅保留列兼容存量数据）';

-- 历史数据可选迁移：待代码完成 ft→style 切换后，可取消注释将存量 ft 统一改写为 style
-- UPDATE `knowledge_base` SET `chat_engine` = 'style' WHERE `chat_engine` = 'ft';

-- ---------- 微调相关表（不存在则创建；已存在则跳过，由后续 MODIFY 对齐结构） ----------
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
    `base_model`     VARCHAR(64)  DEFAULT NULL COMMENT '【已停用/可空】基座模型（风格蒸馏不再使用，保留列兼容存量数据）',
    `model_name`     VARCHAR(128) DEFAULT NULL COMMENT '【已停用/可空】微调后模型名称（风格蒸馏不再使用，保留列兼容存量数据）',
    `zhipu_job_id`   VARCHAR(128) DEFAULT NULL COMMENT '【已停用/可空】智谱官方微调任务ID（风格蒸馏不再使用，保留列兼容存量数据）',
    `zhipu_model_id` VARCHAR(128) DEFAULT NULL COMMENT '【已停用/可空】智谱官方微调模型ID（风格蒸馏不再使用，保留列兼容存量数据）',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTING' COMMENT '状态：SUBMITTING/TRAINING/SUCCEEDED/FAILED/CANCELLED',
    `hyperparams`    VARCHAR(1024) DEFAULT NULL COMMENT '超参数 JSON（n_epochs, batch_size, learning_rate_multiplier）',
    `error_message`  VARCHAR(512) DEFAULT NULL COMMENT '训练失败原因',
    `progress`       TINYINT      DEFAULT 0 COMMENT '训练进度百分比（0-100，按状态映射估算）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_dataset_id` (`dataset_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风格生成任务表（官方微调已停用，风格蒸馏替代）';

CREATE TABLE IF NOT EXISTS `evaluation_record` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评测ID',
    `kb_id`             BIGINT       NOT NULL COMMENT '所属知识库ID',
    `question`          TEXT         NOT NULL COMMENT '测试问题',
    `rag_answer`        TEXT         DEFAULT NULL COMMENT '纯 RAG 链路回答',
    `style_answer`      TEXT         DEFAULT NULL COMMENT 'RAG+风格 链路回答',
    `rag_score`         TINYINT      DEFAULT NULL COMMENT '纯 RAG 回答人工评分（1-5）',
    `style_score`       TINYINT      DEFAULT NULL COMMENT 'RAG+风格 回答人工评分（1-5）',
    `auto_score_rag`    DECIMAL(3,2) DEFAULT NULL COMMENT '纯 RAG 回答自动评测分（0.00-1.00）',
    `auto_score_style`  DECIMAL(3,2) DEFAULT NULL COMMENT 'RAG+风格 回答自动评测分（0.00-1.00）',
    `evaluator_comment` VARCHAR(512) DEFAULT NULL COMMENT '自动评测评语',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测记录表';

-- ---------- evaluation_record：v1 存量表 ft_* 列重命名为 style_*（评测语义改为 纯RAG vs RAG+风格） ----------
-- 新库上一步已按新列名建表，以下 RENAME 自动跳过；仅对 v1 已建表（ft_* 列）生效
CALL `sp_rename_column_if_present`('evaluation_record', 'ft_answer', 'style_answer',
    'ALTER TABLE `evaluation_record` CHANGE COLUMN `ft_answer` `style_answer` TEXT DEFAULT NULL COMMENT ''RAG+风格 链路回答''');
CALL `sp_rename_column_if_present`('evaluation_record', 'ft_score', 'style_score',
    'ALTER TABLE `evaluation_record` CHANGE COLUMN `ft_score` `style_score` TINYINT DEFAULT NULL COMMENT ''RAG+风格 回答人工评分（1-5）''');
CALL `sp_rename_column_if_present`('evaluation_record', 'auto_score_ft', 'auto_score_style',
    'ALTER TABLE `evaluation_record` CHANGE COLUMN `auto_score_ft` `auto_score_style` DECIMAL(3,2) DEFAULT NULL COMMENT ''RAG+风格 回答自动评测分（0.00-1.00）''');

-- ---------- fine_tune_job：智谱官方微调字段停用（可空化，仅保留历史数据） ----------
-- MODIFY 幂等，可重复执行；对 v1 已建表（DEFAULT ''）与新建表均生效
ALTER TABLE `fine_tune_job`
    MODIFY COLUMN `base_model`     VARCHAR(64)  DEFAULT NULL COMMENT '【已停用/可空】基座模型（风格蒸馏不再使用，保留列兼容存量数据）',
    MODIFY COLUMN `model_name`     VARCHAR(128) DEFAULT NULL COMMENT '【已停用/可空】微调后模型名称（风格蒸馏不再使用，保留列兼容存量数据）',
    MODIFY COLUMN `zhipu_job_id`   VARCHAR(128) DEFAULT NULL COMMENT '【已停用/可空】智谱官方微调任务ID（风格蒸馏不再使用，保留列兼容存量数据）',
    MODIFY COLUMN `zhipu_model_id` VARCHAR(128) DEFAULT NULL COMMENT '【已停用/可空】智谱官方微调模型ID（风格蒸馏不再使用，保留列兼容存量数据）';

-- 历史空串归一化为 NULL（幂等，重复执行影响 0 行）
UPDATE `fine_tune_job` SET `zhipu_job_id`   = NULL WHERE `zhipu_job_id`   = '';
UPDATE `fine_tune_job` SET `zhipu_model_id` = NULL WHERE `zhipu_model_id` = '';

-- ---------- 清理幂等工具存储过程 ----------
DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;
DROP PROCEDURE IF EXISTS `sp_rename_column_if_present`;
