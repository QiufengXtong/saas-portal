package com.xtong.saas.system.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 承载租户编码、用户名和原始密码，并规范化登录身份但不改写密码。 */
public record LoginRequest(
        @NotBlank @Size(max = 64) String tenantCode,
        @NotBlank @Size(max = 64) String username,
        @NotBlank String password) {

    /** 统一身份查找与失败锁定所用的大小写和首尾空白语义。 */
    public LoginRequest {
        tenantCode = normalizeIdentity(tenantCode);
        username = normalizeIdentity(username);
    }

    /** 避免把 BCrypt 无法完整处理的超长 UTF-8 密码送入认证流程。 */
    @jakarta.validation.constraints.AssertTrue(message = "密码 UTF-8 长度不能超过72字节")
    public boolean isPasswordWithinUtf8Limit() {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static String normalizeIdentity(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
