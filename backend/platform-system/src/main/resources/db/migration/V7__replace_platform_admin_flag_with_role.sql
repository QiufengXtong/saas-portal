-- 将既有平台管理员资格转换为受保护角色；角色关联成为唯一身份来源。
-- 使用现有最大主键之后的值，避免与已有雪花 ID 冲突。
INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, built_in)
SELECT existing.max_id + ROW_NUMBER() OVER (ORDER BY admins.tenant_id),
       admins.tenant_id, 'PLATFORM_ADMIN', '平台管理员', 'ENABLED', 1
FROM (SELECT DISTINCT tenant_id FROM sys_user WHERE platform_admin = 1 AND deleted = 0) admins
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_role) existing;

INSERT INTO sys_user_role (id, tenant_id, user_id, role_id)
SELECT existing.max_id + ROW_NUMBER() OVER (ORDER BY u.tenant_id, u.id),
       u.tenant_id, u.id, r.id
FROM sys_user u
INNER JOIN sys_role r ON r.tenant_id = u.tenant_id
    AND r.role_code = 'PLATFORM_ADMIN' AND r.built_in = 1 AND r.deleted = 0
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_user_role) existing
WHERE u.platform_admin = 1 AND u.deleted = 0;

UPDATE sys_user SET auth_version = auth_version + 1 WHERE deleted = 0;
ALTER TABLE sys_user DROP COLUMN platform_admin;
