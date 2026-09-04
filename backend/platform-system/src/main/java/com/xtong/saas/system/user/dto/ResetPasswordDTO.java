package com.xtong.saas.system.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

/** 表示管理员为当前租户目标用户重置密码的命令。 */
public record ResetPasswordDTO(@NotBlank @Size(min = 8) String password) {

    /** 校验 BCrypt 输入不会超过其允许的 UTF-8 字节长度。 */
    @AssertTrue(message = "密码 UTF-8 长度不能超过72字节")
    public boolean isPasswordWithinUtf8Limit() {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
