package com.xtong.saas.system.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 表示在当前受信租户中创建普通角色的不可变编码和展示名称。 */
public record CreateRoleDTO(
        @NotBlank @Size(max = 64) String roleCode,
        @NotBlank @Size(max = 128) String roleName) {
}
