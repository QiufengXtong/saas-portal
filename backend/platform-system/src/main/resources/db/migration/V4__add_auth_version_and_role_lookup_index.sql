-- 文件作用：V4 增加用户持久认证版本，并优化按角色查找受影响用户的索引。

ALTER TABLE sys_user
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 COMMENT '认证安全版本';

CREATE INDEX idx_user_role_role ON sys_user_role (tenant_id, role_id, user_id);
