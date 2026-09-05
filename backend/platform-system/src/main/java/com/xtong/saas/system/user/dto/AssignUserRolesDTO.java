package com.xtong.saas.system.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** 表示整体替换当前租户目标用户角色集合的命令。 */
public record AssignUserRolesDTO(@NotNull Set<@NotNull Long> roleIds) {
}
