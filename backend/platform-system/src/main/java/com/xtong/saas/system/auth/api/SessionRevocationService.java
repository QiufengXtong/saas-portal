package com.xtong.saas.system.auth.api;

/** 向用户和角色领域公开会话撤销能力，并隐藏底层会话存储实现细节。 */
public interface SessionRevocationService {

    /** 撤销指定会话。 */
    void revokeSession(String sessionId);

    /** 撤销指定租户用户的全部会话。 */
    void revokeAllUserSessions(long tenantId, long userId);
}
