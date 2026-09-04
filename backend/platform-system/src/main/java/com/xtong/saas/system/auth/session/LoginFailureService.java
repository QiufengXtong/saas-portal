package com.xtong.saas.system.auth.session;

/** 定义租户用户名维度的登录失败限制和成功后清理协议。 */
public interface LoginFailureService {

    void assertAllowed(String tenantCode, String username);

    void recordFailure(String tenantCode, String username);

    void clear(String tenantCode, String username);
}
