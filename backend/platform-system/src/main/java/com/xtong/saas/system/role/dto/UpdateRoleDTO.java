package com.xtong.saas.system.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 表示普通角色可更新的展示名称；角色编码不在该命令中以保持创建后不可变。 */
public record UpdateRoleDTO(@NotBlank @Size(max = 128) String roleName) {
}
