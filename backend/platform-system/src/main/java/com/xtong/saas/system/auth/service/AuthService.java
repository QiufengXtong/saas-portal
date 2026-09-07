package com.xtong.saas.system.auth.service;

import com.xtong.saas.system.auth.dto.LoginRequest;
import com.xtong.saas.system.auth.dto.RefreshTokenRequest;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.vo.CurrentUserVO;
import com.xtong.saas.system.auth.vo.TokenResponse;

/** 定义登录、原子刷新、当前会话退出和当前用户读取用例。 */
public interface AuthService {

    /** 校验租户和用户凭据，创建会话并签发令牌。 */
    TokenResponse login(LoginRequest request);

    /** 原子轮换 Refresh Token，并签发新的访问令牌。 */
    TokenResponse refresh(RefreshTokenRequest request);

    /** 删除指定会话，完成当前用户退出。 */
    void logout(String sessionId);

    /** 读取并校验当前会话对应的用户信息。 */
    CurrentUserVO currentUser(AuthenticatedUser principal);
}
