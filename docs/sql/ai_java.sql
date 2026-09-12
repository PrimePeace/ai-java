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