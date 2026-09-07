package com.xtong.saas.system.auth.session;

/** 定义租户用户名维度的登录失败限制和成功后清理协议。 */
public interface LoginFailureService {

    /** 校验指定租户账号当前是否允许继续尝试登录。 */
    void assertAllowed(String tenantCode, String username);

    /** 记录指定租户账号的一次登录失败。 */
    void recordFailure(String tenantCode, String username);

    /** 清除指定租户账号累计的登录失败状态。 */
    void clear(String tenantCode, String username);
}
