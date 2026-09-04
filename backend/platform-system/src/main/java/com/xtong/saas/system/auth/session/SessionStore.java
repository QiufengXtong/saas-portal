package com.xtong.saas.system.auth.session;

import com.xtong.saas.system.auth.model.AuthSession;

import java.time.Duration;
import java.util.Optional;

/** 定义独立登录会话、Refresh Token 轮换及用户全部会话撤销协议。 */
public interface SessionStore {

    /** 原子创建会话；会话 ID 或 Refresh 摘要碰撞时返回 false，且不覆盖任何已有身份。 */
    boolean create(AuthSession session, String refreshTokenHash, Duration ttl);

    Optional<AuthSession> find(String sessionId);

    /** 原子消费旧摘要并轮换为新摘要；旧摘要无效、会话已撤销或新摘要碰撞时返回空。 */
    Optional<AuthSession> rotateRefreshToken(
            String currentRefreshTokenHash,
            String newRefreshTokenHash,
            Duration ttl);

    void delete(String sessionId);

    void deleteAll(long tenantId, long userId);
}
