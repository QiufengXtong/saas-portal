package com.xtong.saas.system.auth.controller;

import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.auth.dto.LoginRequest;
import com.xtong.saas.system.auth.dto.RefreshTokenRequest;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.service.AuthService;
import com.xtong.saas.system.auth.vo.CurrentUserVO;
import com.xtong.saas.system.auth.vo.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 暴露登录、刷新、当前会话退出和当前认证用户读取 HTTP API。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 创建认证控制器。
     *
     * @param authService 认证用例服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 使用租户编码、用户名和密码完成登录并创建会话。
     *
     * @param request 登录凭据
     * @return Access Token 和 Refresh Token
     */
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 使用一次性 Refresh Token 轮换会话令牌。
     *
     * @param request Refresh Token 请求参数
     * @return 轮换后的 Access Token 和 Refresh Token
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request));
    }

    /**
     * 注销当前认证会话。
     *
     * @param principal 当前认证用户
     * @return 无数据的成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(principal.sessionId());
        return Result.success();
    }

    /**
     * 查询当前会话对应的认证用户及权限。
     *
     * @param principal 当前认证用户
     * @return 当前用户信息和权限集合
     */
    @GetMapping("/me")
    public Result<CurrentUserVO> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Result.success(authService.currentUser(principal));
    }
}
