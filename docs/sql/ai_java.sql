-- 用户表
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname` VARCHAR(64) DEFAULT '' COMMENT '昵称',
    `email` VARCHAR(128) DEFAULT '' COMMENT '邮箱',
    `phone` VARCHAR(32) DEFAULT '' COMMENT '手机号',
    `login_ip` VARCHAR(64) DEFAULT '' COMMENT '最后登录IP',
    `login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `login_fail_count` INT DEFAULT 0 COMMENT '连续登录失败次数',
    `lock_time` DATETIME DEFAULT NULL COMMENT '账号锁定时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 审计日志表（等保三级：记录登录、数据变更、权限变更等敏感操作）
CREATE TABLE `audit_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id`          BIGINT       DEFAULT NULL COMMENT '操作用户ID（未登录为NULL）',
    `username`         VARCHAR(64)  DEFAULT NULL COMMENT '操作用户名',
    `operation_type`   VARCHAR(32)  NOT NULL COMMENT '操作类型（AuditLogType枚举名）',
    `operation_module` VARCHAR(64)  DEFAULT NULL COMMENT '业务模块',
    `description`      VARCHAR(256) NOT NULL COMMENT '操作描述',
    `request_uri`      VARCHAR(256) DEFAULT NULL COMMENT '请求URI',
    `request_method`   VARCHAR(8)   DEFAULT NULL COMMENT '请求方法',
    `request_params`   TEXT         DEFAULT NULL COMMENT '请求参数（JSON，已脱敏截断）',
    `ip_address`       VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    `user_agent`       VARCHAR(256) DEFAULT NULL COMMENT 'User-Agent',
    `result`           VARCHAR(16)  NOT NULL COMMENT '操作结果（SUCCESS/FAILURE/PARTIAL）',
    `error_message`    VARCHAR(512) DEFAULT NULL COMMENT '异常信息（操作失败时）',
    `execution_time`   INT          NOT NULL COMMENT '执行时长（毫秒）',
    `method_signature` VARCHAR(256) DEFAULT NULL COMMENT '被拦截方法签名',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_operation_type` (`operation_type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';


-- ==================== ai-agent 智能体模块 ====================

-- 知识库表
CREATE TABLE `knowledge_base` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
    `name`        VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(256) DEFAULT '' COMMENT '知识库描述',
    `prompt_template_id` BIGINT DEFAULT NULL COMMENT '绑定的提示词模板ID（NULL=默认模板）',
    `chat_engine` VARCHAR(16)  NOT NULL DEFAULT 'rag' COMMENT '问答引擎（rag/style/auto：rag=纯RAG；style=RAG+风格提示词；auto=有风格提示词则叠加，否则纯RAG）',
    `ft_model_id` VARCHAR(128) DEFAULT NULL COMMENT '【已停用】官方微调模型ID（风格蒸馏替代官方微调；代码已移除映射，仅保留列兼容存量数据）',
    `style_prompt` TEXT        DEFAULT NULL COMMENT '风格提示词（风格蒸馏产物，NULL=未生成；style/auto 引擎时拼入系统提示词）',
    `user_id`     BIGINT       NOT NULL COMMENT '创建者用户ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 知识库文档表（摄取状态机：UPLOADED → PROCESSING → COMPLETED/FAILED）
CREATE TABLE `knowledge_document` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `kb_id`         BIGINT       NOT NULL COMMENT '所属知识库ID',
    `file_name`     VARCHAR(256) NOT NULL COMMENT '原始文件名',
    `file_type`     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '文件类型（pdf/docx/md/txt）',
    `file_size`     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `file_path`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '服务器存储路径',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED' COMMENT '处理状态（DocStatus枚举名）',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '处理失败原因（FAILED时）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_kb_status_time` (`kb_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- 文档切片表（向量只存 Redis，原文存此表用于引用溯源与索引重建）
CREATE TABLE `document_chunk` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '切片ID',
    `doc_id`      BIGINT      NOT NULL COMMENT '所属文档ID',
    `kb_id`       BIGINT      NOT NULL COMMENT '所属知识库ID（冗余，清理向量时免join）',
    `chunk_index` INT         NOT NULL COMMENT '切片序号（文档内从0递增）',
    `content`     MEDIUMTEXT  NOT NULL COMMENT '切片原文',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_index` (`doc_id`, `chunk_index`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切片表';

-- 对话会话表
CREATE TABLE `chat_session` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
    `kb_id`       BIGINT       NOT NULL COMMENT '关联知识库ID（一个会话绑定一个知识库）',
    `title`       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '会话标题（默认取首条提问前20字符）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_update` (`user_id`, `update_time`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 对话消息表（追加型，无 update_time；idx_session_id 隐含主键后缀，等效 (session_id, id)）
CREATE TABLE `chat_message` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id`  BIGINT      NOT NULL COMMENT '所属会话ID',
    `role`        VARCHAR(16) NOT NULL COMMENT '角色（user/assistant）',
    `content`     MEDIUMTEXT  NOT NULL COMMENT '消息内容',
    `citations`   MEDIUMTEXT  DEFAULT NULL COMMENT '引用JSON数组（仅assistant消息：[{docId,docName,chunkIndex,content,score}]）',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';


-- ==================== Prompt 工程 ====================

-- 提示词模板表
CREATE TABLE `prompt_template` (
   `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
   `user_id`         BIGINT       NOT NULL COMMENT '所属用户ID',
   `name`            VARCHAR(64)  NOT NULL COMMENT '模板名称',
   `description`     VARCHAR(256) DEFAULT '' COMMENT '模板描述',
   `system_template` TEXT         NOT NULL COMMENT '系统提示词模板（变量 {kbName} {kbDescription}）',
   `user_template`   TEXT         NOT NULL COMMENT '用户消息模板（变量 {question} {references} {referencesBlock}）',
   `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
   `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
   PRIMARY KEY (`id`),
   INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';


-- ==================== 模型微调模块 ====================

-- 微调数据集表（状态机：GENERATING → READY/FAILED）
CREATE TABLE `fine_tune_dataset` (
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

-- 风格生成任务表（官方微调已停用，由风格蒸馏替代；表结构保留兼容存量数据，状态机：SUBMITTING → TRAINING → SUCCEEDED/FAILED/CANCELLED）
CREATE TABLE `fine_tune_job` (
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

-- 评测记录表（纯 RAG vs RAG+风格 并行对比）
CREATE TABLE `evaluation_record` (
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