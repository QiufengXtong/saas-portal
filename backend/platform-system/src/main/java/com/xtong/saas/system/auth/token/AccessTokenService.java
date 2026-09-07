package com.xtong.saas.system.auth.token;

import com.xtong.saas.system.auth.model.AuthenticatedUser;

/** 定义短期 Access Token 的签发和可信声明解析边界。 */
public interface AccessTokenService {

    /** 为已认证用户签发短期访问令牌。 */
    String issue(AuthenticatedUser user);

    /** 解析并校验访问令牌，恢复可信认证主体。 */
    AuthenticatedUser parse(String token);
}
