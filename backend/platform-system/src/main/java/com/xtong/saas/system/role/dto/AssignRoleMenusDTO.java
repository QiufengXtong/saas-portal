package com.xtong.saas.system.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** 表示对普通角色进行整体替换的全局菜单 ID 集合。 */
public record AssignRoleMenusDTO(@NotNull Set<@NotNull Long> menuIds) {
}
