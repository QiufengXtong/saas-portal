package com.xtong.saas.system.auth.api;

/** 向用户和角色领域公开会话撤销能力，并隐藏底层会话存储实现细节。 */
public interface SessionRevocationService {

    void revokeSession(String sessionId);

    void revokeAllUserSessions(long tenantId, long userId);
}
