-- 文件作用：建立平台管理员身份与权限范围边界，阻止租户角色获得全局菜单维护权限。

ALTER TABLE sys_user
    ADD COLUMN platform_admin TINYINT NOT NULL DEFAULT 0 COMMENT '平台管理员标记';

ALTER TABLE sys_menu
    ADD COLUMN permission_scope VARCHAR(16) NOT NULL DEFAULT 'TENANT' COMMENT '权限适用范围';

UPDATE sys_menu
SET permission_scope = 'PLATFORM'
WHERE id IN (130, 13001, 13002, 13003, 13004, 13005, 13006, 13007);

-- 升级已有环境时，仅将最早租户中的首个有效内置租户管理员提升为平台管理员。
UPDATE sys_user
SET platform_admin = 1
WHERE id = (
    SELECT initial_admin.user_id
    FROM (
        SELECT u.id AS user_id
        FROM sys_user u
        INNER JOIN sys_tenant t ON t.id = u.tenant_id
        INNER JOIN sys_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
        INNER JOIN sys_role r ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id
        WHERE u.deleted = 0 AND u.status = 'ENABLED'
          AND t.deleted = 0 AND t.status = 'ENABLED'
          AND r.deleted = 0 AND r.status = 'ENABLED'
          AND r.built_in = 1 AND r.role_code = 'TENANT_ADMIN'
        ORDER BY t.created_at, t.id, u.created_at, u.id
        LIMIT 1
    ) initial_admin
);

-- 平台资源不允许残留在租户角色授权中；升级前权限将在重新登录后按新范围重建。
DELETE FROM sys_role_menu
WHERE menu_id IN (
    SELECT id FROM sys_menu WHERE permission_scope = 'PLATFORM'
);

UPDATE sys_user SET auth_version = auth_version + 1 WHERE deleted = 0;
