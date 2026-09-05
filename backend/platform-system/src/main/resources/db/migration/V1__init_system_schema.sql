-- 文件作用：V1 创建 System IAM 的租户、用户、角色、全局菜单及物理关联表结构。

CREATE TABLE sys_tenant (
    id BIGINT NOT NULL COMMENT '雪花主键',
    tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
    tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '租户状态',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_tenant_code UNIQUE (tenant_code)
) COMMENT '租户';

CREATE TABLE sys_user (
    id BIGINT NOT NULL COMMENT '雪花主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码摘要',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    email VARCHAR(254) NULL COMMENT '电子邮箱',
    mobile VARCHAR(32) NULL COMMENT '手机号码',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '用户状态',
    password_changed_at TIMESTAMP(3) NULL COMMENT '密码最近修改时间',
    last_login_at TIMESTAMP(3) NULL COMMENT '最近登录时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_tenant_username UNIQUE (tenant_id, username)
) COMMENT '租户系统用户';

CREATE INDEX idx_sys_user_tenant_status ON sys_user (tenant_id, status);

CREATE TABLE sys_role (
    id BIGINT NOT NULL COMMENT '雪花主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '角色状态',
    built_in TINYINT NOT NULL DEFAULT 0 COMMENT '内置角色标记',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_tenant_code UNIQUE (tenant_id, role_code)
) COMMENT '租户角色';

CREATE INDEX idx_sys_role_tenant_status ON sys_role (tenant_id, status);

CREATE TABLE sys_menu (
    id BIGINT NOT NULL COMMENT '雪花主键',
    parent_id BIGINT NULL COMMENT '父节点主键',
    name VARCHAR(128) NOT NULL COMMENT '菜单或权限名称',
    type VARCHAR(16) NOT NULL COMMENT '节点类型',
    route_path VARCHAR(255) NULL COMMENT '前端路由',
    component VARCHAR(255) NULL COMMENT '前端组件',
    icon VARCHAR(64) NULL COMMENT '图标标识',
    permission_code VARCHAR(128) NULL COMMENT '权限码',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    visible TINYINT NOT NULL DEFAULT 1 COMMENT '可见标记',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '菜单状态',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_menu_permission UNIQUE (permission_code)
) COMMENT '全局菜单和权限资源';

CREATE INDEX idx_sys_menu_parent_sort ON sys_menu (parent_id, sort_order);

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL COMMENT '雪花主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    user_id BIGINT NOT NULL COMMENT '用户主键',
    role_id BIGINT NOT NULL COMMENT '角色主键',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_role UNIQUE (tenant_id, user_id, role_id)
) COMMENT '用户角色物理关联';

CREATE TABLE sys_role_menu (
    id BIGINT NOT NULL COMMENT '雪花主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    role_id BIGINT NOT NULL COMMENT '角色主键',
    menu_id BIGINT NOT NULL COMMENT '全局菜单主键',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_menu UNIQUE (tenant_id, role_id, menu_id)
) COMMENT '角色菜单物理关联';
