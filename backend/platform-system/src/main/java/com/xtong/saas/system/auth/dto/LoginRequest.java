package com.xtong.saas.system.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import com.xtong.saas.system.identity.IdentityNormalizer;

/** 承载租户编码、用户名和原始密码，具体身份校验统一由认证服务边界完成。 */
public record LoginRequest(
        @NotBlank @Size(max = 64) String tenantCode,
        @NotBlank @Size(max = 64) String username,
        @NotBlank String password) {

    /** 对合法身份提前规范化；不合法值保留给认证服务统一映射为无效凭据。 */
    public LoginRequest {
        tenantCode = IdentityNormalizer.normalizeForLogin(tenantCode).orElse(tenantCode);
        username = IdentityNormalizer.normalizeForLogin(username).orElse(username);
    }

    /** 避免把 BCrypt 无法完整处理的超长 UTF-8 密码送入认证流程。 */
    @jakarta.validation.constraints.AssertTrue(message = "密码 UTF-8 长度不能超过72字节")
    public boolean isPasswordWithinUtf8Limit() {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
