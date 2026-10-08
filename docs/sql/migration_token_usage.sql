-- ==================== 对话消息 token 用量 存量库迁移脚本 ====================
-- 适用于已部署的 ai_java 库：在已有表结构上增量执行
-- 新库直接执行 ai_java.sql 即可，无需执行本文件
--
-- 说明：
--   1. 用途：chat_message 表新增 prompt_tokens / completion_tokens 两列，
--      用于记录 assistant 消息的输入/输出 token 用量（user 消息为 NULL）
--   2. 本脚本可重复执行（幂等）：加列前查 information_schema 判存在
--   3. 本脚本使用 DELIMITER 定义存储过程，请用 mysql CLI / Navicat / DataGrip 等客户端整文件执行

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

-- ---------- chat_message：token 用量统计（仅 assistant 消息有值，追加在 citations 之后） ----------
CALL `sp_add_column_if_missing`('chat_message', 'prompt_tokens',
    'ALTER TABLE `chat_message` ADD COLUMN `prompt_tokens` INT DEFAULT NULL COMMENT ''输入 token 数（assistant 消息）'' AFTER `citations`');

CALL `sp_add_column_if_missing`('chat_message', 'completion_tokens',
    'ALTER TABLE `chat_message` ADD COLUMN `completion_tokens` INT DEFAULT NULL COMMENT ''输出 token 数（assistant 消息）'' AFTER `prompt_tokens`');

-- ---------- 清理幂等工具存储过程 ----------
DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;
