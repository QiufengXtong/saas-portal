package com.xtong.saas.system.auth.token;

import com.xtong.saas.system.auth.model.AuthenticatedUser;

/** 定义短期 Access Token 的签发和可信声明解析边界。 */
public interface AccessTokenService {

    String issue(AuthenticatedUser user);

    AuthenticatedUser parse(String token);
}
