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