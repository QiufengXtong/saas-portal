package com.xtong.saas.system.role.dto;

import com.xtong.saas.system.role.enums.RoleStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 表示当前受信租户角色列表的分页、编码、名称和状态筛选条件。 */
public record RoleQueryDTO(
        @Min(1) long pageNum,
        @Min(1) @Max(500) long pageSize,
        @Size(max = 64) String roleCode,
        @Size(max = 128) String roleName,
        RoleStatus status) {
}
