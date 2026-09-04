package com.xtong.saas.system.user.dto;

import com.xtong.saas.system.user.enums.UserStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 表示当前租户用户列表的分页、用户名和状态筛选条件。 */
public record UserQueryDTO(
        @Min(1) long pageNum,
        @Min(1) @Max(500) long pageSize,
        @Size(max = 64) String username,
        UserStatus status) {
}
