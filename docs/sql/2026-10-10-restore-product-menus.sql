-- 补回网页端三个页面，用户和管理员都能看到。仅网页，不进 App。

INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `menu_type`, `permission`, `visible`, `client`, `status`)
VALUES
    (13, 0, '仪表盘', '/dashboard', 'dashboard/DashboardView', 'grid', 1, 'MENU', NULL, 1, 'WEB', 1),
    (14, 0, '提示词模板', '/prompt', 'prompt/PromptTemplateView', 'document', 15, 'MENU', 'prompt:use', 1, 'WEB', 1),
    (15, 0, '回答风格', '/fine-tune', 'ft/FineTuneView', 'sparkles', 25, 'MENU', 'ft:use', 1, 'WEB', 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `icon` = VALUES(`icon`),
    `sort` = VALUES(`sort`),
    `permission` = VALUES(`permission`),
    `visible` = VALUES(`visible`),
    `client` = VALUES(`client`),
    `status` = VALUES(`status`);

INSERT IGNORE INTO `role_menu` (`role_id`, `menu_id`) VALUES
    (1, 13), (1, 14), (1, 15),
    (2, 1), (2, 2), (2, 3), (2, 4),
    (2, 13), (2, 14), (2, 15);
