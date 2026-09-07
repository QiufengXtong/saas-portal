-- 文件作用：为全局菜单增加内置保护标识，并初始化菜单与权限管理入口及操作权限。

ALTER TABLE sys_menu
    ADD COLUMN built_in TINYINT NOT NULL DEFAULT 0 COMMENT '内置菜单保护标记';

UPDATE sys_menu
SET built_in = 1
WHERE id IN (
    100, 110, 120, 10001, 10002,
    11001, 11002, 11003, 11004, 11005, 11006, 11007, 11008, 11009,
    12001, 12002, 12003, 12004, 12005, 12006, 12007, 12008
);

INSERT INTO sys_menu
    (id, parent_id, name, type, route_path, component, icon, permission_code,
     sort_order, visible, status, built_in)
VALUES
    (130, 100, '菜单管理', 'MENU', '/system/menus', 'system/menu/index', 'menu', NULL,
     130, 1, 'ENABLED', 1),
    (13001, 130, '菜单列表', 'BUTTON', NULL, NULL, NULL, 'system:menu:list',
     1, 0, 'ENABLED', 1),
    (13002, 130, '菜单详情', 'BUTTON', NULL, NULL, NULL, 'system:menu:detail',
     2, 0, 'ENABLED', 1),
    (13003, 130, '创建菜单', 'BUTTON', NULL, NULL, NULL, 'system:menu:create',
     3, 0, 'ENABLED', 1),
    (13004, 130, '更新菜单', 'BUTTON', NULL, NULL, NULL, 'system:menu:update',
     4, 0, 'ENABLED', 1),
    (13005, 130, '启用菜单', 'BUTTON', NULL, NULL, NULL, 'system:menu:enable',
     5, 0, 'ENABLED', 1),
    (13006, 130, '停用菜单', 'BUTTON', NULL, NULL, NULL, 'system:menu:disable',
     6, 0, 'ENABLED', 1),
    (13007, 130, '删除菜单', 'BUTTON', NULL, NULL, NULL, 'system:menu:delete',
     7, 0, 'ENABLED', 1);

-- 旧会话不包含本次新增权限，递增版本使其在下一次请求时失效并重新建立权限快照。
UPDATE sys_user SET auth_version = auth_version + 1 WHERE deleted = 0;
