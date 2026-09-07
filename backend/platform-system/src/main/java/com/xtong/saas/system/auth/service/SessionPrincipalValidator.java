package com.xtong.saas.system.auth.service;

import com.xtong.saas.system.auth.model.AuthSession;

/** 定义每次请求对 Redis 会话与数据库用户安全状态的一致性校验。 */
@FunctionalInterface
public interface SessionPrincipalValidator {

    /** 校验会话主体与数据库中的用户安全状态是否一致。 */
    boolean isValid(AuthSession session);
}
