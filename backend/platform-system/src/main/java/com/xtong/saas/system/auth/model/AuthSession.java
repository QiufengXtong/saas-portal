package com.xtong.saas.system.auth.model;

import java.util.Objects;
import java.util.Set;

/** 表示 Redis 中保存的独立登录会话及其当前 Refresh Token 摘要。 */
public record AuthSession(
        String sessionId,
        long tenantId,
        long userId,
        String username,
        String displayName,
        Set<String> permissions,
        long authVersion,
        String refreshTokenHash) {

    /** 为既有调用方提供认证版本零的兼容构造入口。 */
    public AuthSession(
            String sessionId, long tenantId, long userId, String username, String displayName,
            Set<String> permissions, String refreshTokenHash) {
        this(sessionId, tenantId, userId, username, displayName, permissions, 0L, refreshTokenHash);
    }

    /** 固化会话字段和权限快照，避免存储内容被调用方后续修改。 */
    public AuthSession {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(refreshTokenHash, "refreshTokenHash must not be null");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
