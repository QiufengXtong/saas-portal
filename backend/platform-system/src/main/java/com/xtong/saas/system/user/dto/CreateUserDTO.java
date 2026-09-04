package com.xtong.saas.system.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 表示在当前受信租户中创建用户及其初始角色关系的命令。 */
public record CreateUserDTO(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(min = 8) String password,
        @Email @Size(max = 254) String email,
        @Size(max = 32) String mobile,
        @NotNull Set<@NotNull Long> roleIds) {

    /** 校验 BCrypt 输入不会超过其允许的 UTF-8 字节长度。 */
    @jakarta.validation.constraints.AssertTrue(message = "密码 UTF-8 长度不能超过72字节")
    public boolean isPasswordWithinUtf8Limit() {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
