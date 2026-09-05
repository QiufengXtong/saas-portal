package com.xtong.saas.system.auth.service;

import com.xtong.saas.system.auth.dto.LoginRequest;
import com.xtong.saas.system.auth.dto.RefreshTokenRequest;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.vo.CurrentUserVO;
import com.xtong.saas.system.auth.vo.TokenResponse;

/** 定义登录、原子刷新、当前会话退出和当前用户读取用例。 */
public interface AuthService {

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    void logout(String sessionId);

    CurrentUserVO currentUser(AuthenticatedUser principal);
}
