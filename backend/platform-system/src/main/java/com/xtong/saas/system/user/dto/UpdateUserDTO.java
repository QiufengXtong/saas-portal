package com.xtong.saas.system.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 表示可更新的用户资料；用户名不在该命令中以保持其创建后不可变。 */
public record UpdateUserDTO(
        @NotBlank @Size(max = 128) String displayName,
        @Email @Size(max = 254) String email,
        @Size(max = 32) String mobile) {
}
