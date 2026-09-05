package com.xtong.saas.system.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xtong.saas.common.model.BaseEntity;
import com.xtong.saas.system.role.enums.RoleStatus;
import lombok.Getter;
import lombok.Setter;

/** 表示租户内用于权限聚合的角色持久化模型。 */
@TableName("sys_role")
@Getter
@Setter
public class SystemRole extends BaseEntity {

    private Long tenantId;
    private String roleCode;
    private String roleName;
    private RoleStatus status;
    private Boolean builtIn;
}
