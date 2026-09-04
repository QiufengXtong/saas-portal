-- 文件作用：V2 初始化全局系统管理菜单与 19 个固定 IAM 权限码，不创建任何租户或账号数据。

INSERT INTO sys_menu
    (id, parent_id, name, type, route_path, component, icon, permission_code, sort_order, visible, status)
VALUES
    (100, NULL, '系统管理', 'DIRECTORY', '/system', NULL, 'setting', NULL, 100, 1, 'ENABLED'),
    (110, 100, '用户管理', 'MENU', '/system/users', 'system/user/index', 'user', NULL, 110, 1, 'ENABLED'),
    (120, 100, '角色管理', 'MENU', '/system/roles', 'system/role/index', 'team', NULL, 120, 1, 'ENABLED'),
    (11001, 110, '用户列表', 'BUTTON', NULL, NULL, NULL, 'system:user:list', 1, 0, 'ENABLED'),
    (11002, 110, '用户详情', 'BUTTON', NULL, NULL, NULL, 'system:user:detail', 2, 0, 'ENABLED'),
    (11003, 110, '创建用户', 'BUTTON', NULL, NULL, NULL, 'system:user:create', 3, 0, 'ENABLED'),
    (11004, 110, '更新用户', 'BUTTON', NULL, NULL, NULL, 'system:user:update', 4, 0, 'ENABLED'),
    (11005, 110, '启用用户', 'BUTTON', NULL, NULL, NULL, 'system:user:enable', 5, 0, 'ENABLED'),
    (11006, 110, '停用用户', 'BUTTON', NULL, NULL, NULL, 'system:user:disable', 6, 0, 'ENABLED'),
    (11007, 110, '重置密码', 'BUTTON', NULL, NULL, NULL, 'system:user:reset-password', 7, 0, 'ENABLED'),
    (11008, 110, '删除用户', 'BUTTON', NULL, NULL, NULL, 'system:user:delete', 8, 0, 'ENABLED'),
    (11009, 110, '分配角色', 'BUTTON', NULL, NULL, NULL, 'system:user:assign-role', 9, 0, 'ENABLED'),
    (12001, 120, '角色列表', 'BUTTON', NULL, NULL, NULL, 'system:role:list', 1, 0, 'ENABLED'),
    (12002, 120, '角色详情', 'BUTTON', NULL, NULL, NULL, 'system:role:detail', 2, 0, 'ENABLED'),
    (12003, 120, '创建角色', 'BUTTON', NULL, NULL, NULL, 'system:role:create', 3, 0, 'ENABLED'),
    (12004, 120, '更新角色', 'BUTTON', NULL, NULL, NULL, 'system:role:update', 4, 0, 'ENABLED'),
    (12005, 120, '启用角色', 'BUTTON', NULL, NULL, NULL, 'system:role:enable', 5, 0, 'ENABLED'),
    (12006, 120, '停用角色', 'BUTTON', NULL, NULL, NULL, 'system:role:disable', 6, 0, 'ENABLED'),
    (12007, 120, '删除角色', 'BUTTON', NULL, NULL, NULL, 'system:role:delete', 7, 0, 'ENABLED'),
    (12008, 120, '分配菜单', 'BUTTON', NULL, NULL, NULL, 'system:role:assign-menu', 8, 0, 'ENABLED'),
    (10001, 100, '查看菜单树', 'BUTTON', NULL, NULL, NULL, 'system:menu:tree', 1, 0, 'ENABLED'),
    (10002, 100, '查看权限列表', 'BUTTON', NULL, NULL, NULL, 'system:permission:list', 2, 0, 'ENABLED');
