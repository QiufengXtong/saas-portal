-- 取消旧版租户管理员的内置身份，保留用户、角色及显式菜单授权。
UPDATE sys_role
SET built_in = 0, updated_at = CURRENT_TIMESTAMP
WHERE role_code = 'TENANT_ADMIN' AND built_in = 1;

-- 权限计算不再自动授予租户全权限，旧会话必须重新建立权限快照。
UPDATE sys_user SET auth_version = auth_version + 1 WHERE deleted = 0;
