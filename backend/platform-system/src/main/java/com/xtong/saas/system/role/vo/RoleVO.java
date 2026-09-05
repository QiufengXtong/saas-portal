package com.xtong.saas.system.role.vo;

import com.xtong.saas.system.role.entity.SystemRole;
import com.xtong.saas.system.role.enums.RoleStatus;

import java.time.LocalDateTime;

/** 表示可安全返回给客户端的租户角色视图，所有持久化 ID 均编码为字符串。 */
public record RoleVO(
        String id,
        String tenantId,
        String roleCode,
        String roleName,
        RoleStatus status,
        boolean builtIn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 将角色持久化实体映射为不会泄露内部持久化状态的 API 视图。 */
    public static RoleVO from(SystemRole role) {
        return new RoleVO(
                role.getId().toString(),
                role.getTenantId().toString(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getStatus(),
                Boolean.TRUE.equals(role.getBuiltIn()),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
