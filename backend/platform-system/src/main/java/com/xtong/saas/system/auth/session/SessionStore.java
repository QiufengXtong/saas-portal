package com.xtong.saas.system.auth.session;

import com.xtong.saas.system.auth.model.AuthSession;

import java.time.Duration;
import java.util.Optional;

/** 定义独立登录会话、Refresh Token 轮换及用户全部会话撤销协议。 */
public interface SessionStore {

    void create(AuthSession session, String refreshTokenHash, Duration ttl);

    Optional<AuthSession> find(String sessionId);

    Optional<String> consumeRefreshToken(String refreshTokenHash);

    void replaceRefreshToken(AuthSession session, String newRefreshTokenHash, Duration ttl);

    void delete(String sessionId);

    void deleteAll(long tenantId, long userId);
}
