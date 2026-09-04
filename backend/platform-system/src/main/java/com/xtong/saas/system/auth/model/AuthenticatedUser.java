package com.xtong.saas.system.auth.model;

import java.util.Objects;
import java.util.Set;

/** 表示认证上下文中的租户用户、独立会话及其当前权限。 */
public record AuthenticatedUser(
        long tenantId,
        long userId,
        String sessionId,
        String username,
        Set<String> permissions) {

    /** 复制权限集合，避免认证主体在创建后被外部修改。 */
    public AuthenticatedUser {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
