package com.xtong.saas.system.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 集中定义并校验令牌、登录限制和会话有效期配置。 */
@ConfigurationProperties("saas.auth")
@Validated
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Min(1) int loginFailureLimit,
        @NotNull Duration loginFailureWindow,
        @NotNull Duration loginLockDuration) {

    /** 在配置对象创建时执行跨字段和字节长度校验，确保无效配置不能启动应用。 */
    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwtSecret must contain at least 32 UTF-8 bytes");
        }
        requirePositive(accessTokenTtl, "accessTokenTtl");
        requirePositive(refreshTokenTtl, "refreshTokenTtl");
        requirePositive(loginFailureWindow, "loginFailureWindow");
        requirePositive(loginLockDuration, "loginLockDuration");
        if (accessTokenTtl.compareTo(refreshTokenTtl) >= 0) {
            throw new IllegalArgumentException("accessTokenTtl must be shorter than refreshTokenTtl");
        }
        if (loginFailureLimit < 1) {
            throw new IllegalArgumentException("loginFailureLimit must be at least 1");
        }
    }

    private static void requirePositive(Duration duration, String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }
}
