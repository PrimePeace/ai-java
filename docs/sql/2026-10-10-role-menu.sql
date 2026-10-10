-- 已有 ai_java 库执行一次。新库直接使用 ai_java.sql。
-- 管理员账号不在本脚本创建，由应用启动时读取 APP_ADMIN_PASSWORD 写入。

CREATE TABLE IF NOT EXISTS `role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `code`        VARCHAR(32)  NOT NULL COMMENT '角色编码',
    `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `remark`      VARCHAR(256) DEFAULT '' COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `menu` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级菜单ID，0为根',
    `name`        VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `path`        VARCHAR(128) DEFAULT NULL COMMENT '路由路径',
    `component`   VARCHAR(128) DEFAULT NULL COMMENT '前端组件键',
    `icon`        VARCHAR(64)  DEFAULT NULL COMMENT '图标名',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `menu_type`   VARCHAR(16)  NOT NULL COMMENT 'CATALOG/MENU/BUTTON',
    `permission`  VARCHAR(64)  DEFAULT NULL COMMENT '权限码',
    `visible`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1-显示在菜单 0-仅注册路由',
    `client`      VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT 'WEB/APP/ALL',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单与权限';

CREATE TABLE IF NOT EXISTS `role_menu` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单';

INSERT INTO `role` (`id`, `code`, `name`, `status`, `remark`)
VALUES (1, 'USER', '用户', 1, '普通用户'),
       (2, 'ADMIN', '管理员', 1, '系统管理员')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `menu_type`, `permission`, `visible`, `client`, `status`)
VALUES
    (1, 0, '知识库', '/kb', 'kb/KnowledgeBaseView', 'library', 10, 'MENU', 'kb:view', 1, 'ALL', 1),
    (2, 0, '知识库文档', '/kb/:id', 'kb/KnowledgeBaseDetailView', 'library', 11, 'MENU', 'kb:view', 0, 'ALL', 1),
    (3, 0, '知识库编辑', NULL, NULL, NULL, 12, 'BUTTON', 'kb:edit', 0, 'ALL', 1),
    (4, 0, '智能问答', '/chat', 'chat/ChatView', 'chatbubbles', 20, 'MENU', 'chat:use', 1, 'ALL', 1),
    (13, 0, '仪表盘', '/dashboard', 'dashboard/DashboardView', 'grid', 1, 'MENU', NULL, 1, 'WEB', 1),
    (14, 0, '提示词模板', '/prompt', 'prompt/PromptTemplateView', 'document', 15, 'MENU', 'prompt:use', 1, 'WEB', 1),
    (15, 0, '回答风格', '/fine-tune', 'ft/FineTuneView', 'sparkles', 25, 'MENU', 'ft:use', 1, 'WEB', 1),
    (5, 0, '用户管理', '/system/users', 'system/UserManageView', 'people', 10, 'MENU', 'user:list', 1, 'WEB', 1),
    (6, 0, '封禁用户', NULL, NULL, NULL, 11, 'BUTTON', 'user:ban', 0, 'WEB', 1),
    (7, 0, '调整角色', NULL, NULL, NULL, 12, 'BUTTON', 'user:assign-role', 0, 'WEB', 1),
    (8, 0, '角色管理', '/system/roles', 'system/RoleManageView', 'shield', 20, 'MENU', 'role:view', 1, 'WEB', 1),
    (9, 0, '分配菜单', NULL, NULL, NULL, 21, 'BUTTON', 'role:assign-menu', 0, 'WEB', 1),
    (10, 0, '菜单管理', '/system/menus', 'system/MenuManageView', 'menu', 30, 'MENU', 'menu:view', 1, 'WEB', 1),
    (11, 0, '编辑菜单', NULL, NULL, NULL, 31, 'BUTTON', 'menu:edit', 0, 'WEB', 1),
    (12, 0, '审计日志', '/system/audit', 'system/AuditLogView', 'document', 40, 'MENU', 'audit:view', 1, 'WEB', 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `icon` = VALUES(`icon`),
    `permission` = VALUES(`permission`),
    `visible` = VALUES(`visible`),
    `client` = VALUES(`client`);

INSERT IGNORE INTO `role_menu` (`role_id`, `menu_id`) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 13), (1, 14), (1, 15),
    (2, 1), (2, 2), (2, 3), (2, 4),
    (2, 5), (2, 6), (2, 7), (2, 8), (2, 9), (2, 10), (2, 11), (2, 12),
    (2, 13), (2, 14), (2, 15);

SET @role_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'role_id'
);
SET @ddl = IF(@role_col_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `role_id` BIGINT DEFAULT NULL COMMENT ''角色ID'' AFTER `status`, ADD INDEX `idx_role_id` (`role_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @refresh_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'refresh_invalid_before'
);
SET @ddl = IF(@refresh_col_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `refresh_invalid_before` DATETIME DEFAULT NULL COMMENT ''此时间及之前签发的 Refresh Token 失效'' AFTER `role_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `user` u
    JOIN `role` r ON r.code = 'USER'
SET u.role_id = r.id
WHERE u.role_id IS NULL;

ALTER TABLE `user` MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色ID';
