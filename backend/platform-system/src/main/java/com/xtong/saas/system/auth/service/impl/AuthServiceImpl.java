package com.xtong.saas.system.auth.service.impl;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.dto.LoginRequest;
import com.xtong.saas.system.auth.dto.RefreshTokenRequest;
import com.xtong.saas.system.auth.exception.AuthErrorCode;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.service.AuthService;
import com.xtong.saas.system.auth.session.LoginFailureService;
import com.xtong.saas.system.auth.session.SessionIdGenerator;
import com.xtong.saas.system.auth.session.SessionStore;
import com.xtong.saas.system.auth.token.AccessTokenService;
import com.xtong.saas.system.auth.token.RefreshTokenGenerator;
import com.xtong.saas.system.auth.token.TokenHashService;
import com.xtong.saas.system.auth.vo.CurrentUserVO;
import com.xtong.saas.system.auth.vo.TokenResponse;
import com.xtong.saas.system.menu.service.PermissionService;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.tenant.exception.TenantErrorCode;
import com.xtong.saas.system.tenant.service.TenantService;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.exception.UserErrorCode;
import com.xtong.saas.system.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 编排租户登录、一次性 Refresh Token 轮换和会话读取，不暴露账号存在性。 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final TenantService tenantService;
    private final UserService userService;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final LoginFailureService loginFailureService;
    private final SessionStore sessionStore;
    private final SessionIdGenerator sessionIdGenerator;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenHashService tokenHashService;
    private final AccessTokenService accessTokenService;
    private final AuthProperties authProperties;

    public AuthServiceImpl(
            TenantService tenantService,
            UserService userService,
            PermissionService permissionService,
            PasswordEncoder passwordEncoder,
            LoginFailureService loginFailureService,
            SessionStore sessionStore,
            SessionIdGenerator sessionIdGenerator,
            RefreshTokenGenerator refreshTokenGenerator,
            TokenHashService tokenHashService,
            AccessTokenService accessTokenService,
            AuthProperties authProperties) {
        this.tenantService = tenantService;
        this.userService = userService;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.loginFailureService = loginFailureService;
        this.sessionStore = sessionStore;
        this.sessionIdGenerator = sessionIdGenerator;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenHashService = tokenHashService;
        this.accessTokenService = accessTokenService;
        this.authProperties = authProperties;
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String tenantCode = request.tenantCode();
        String username = request.username();
        SystemTenant tenant = requireLoginTenant(tenantCode);
        loginFailureService.assertAllowed(tenantCode, username);
        SystemUser user = requireLoginUser(tenant.getId(), tenantCode, username);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginFailureService.recordFailure(tenantCode, username);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        Set<String> permissions = permissionService.loadUserPermissions(tenant.getId(), user.getId());
        CreatedSession created = createUniqueSession(tenant.getId(), user, permissions);
        AuthenticatedUser principal = toPrincipal(created.session());
        String accessToken = accessTokenService.issue(principal);
        TenantScope.run(tenant.getId(), () -> userService.recordLoginSuccess(user.getId(), LocalDateTime.now()));
        loginFailureService.clear(tenantCode, username);
        return tokenResponse(accessToken, created.refreshToken());
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String currentHash = tokenHashService.hash(request.refreshToken());
        String newRefreshToken = refreshTokenGenerator.generate();
        String newHash = tokenHashService.hash(newRefreshToken);
        AuthSession rotated = sessionStore.rotateRefreshToken(
                        currentHash, newHash, authProperties.refreshTokenTtl())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        String accessToken = accessTokenService.issue(toPrincipal(rotated));
        return tokenResponse(accessToken, newRefreshToken);
    }

    @Override
    public void logout(String sessionId) {
        sessionStore.delete(sessionId);
    }

    @Override
    public CurrentUserVO currentUser(AuthenticatedUser principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        AuthSession session = sessionStore.find(principal.sessionId())
                .filter(found -> hasSameIdentity(found, principal))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_EXPIRED));
        return new CurrentUserVO(
                Long.toString(session.tenantId()),
                Long.toString(session.userId()),
                session.username(),
                session.displayName(),
                session.permissions());
    }

    private SystemUser requireLoginUser(long tenantId, String tenantCode, String username) {
        try {
            return userService.requireEnabledForLogin(tenantId, username);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != UserErrorCode.USER_NOT_FOUND) {
                throw exception;
            }
            loginFailureService.recordFailure(tenantCode, username);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }

    private SystemTenant requireLoginTenant(String tenantCode) {
        try {
            return tenantService.requireEnabledByCode(tenantCode);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == TenantErrorCode.TENANT_NOT_FOUND) {
                throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
            }
            throw exception;
        }
    }

    private CreatedSession createUniqueSession(long tenantId, SystemUser user, Set<String> permissions) {
        while (true) {
            String sessionId = sessionIdGenerator.generate();
            String refreshToken = refreshTokenGenerator.generate();
            String refreshHash = tokenHashService.hash(refreshToken);
            AuthSession session = new AuthSession(
                    sessionId,
                    tenantId,
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    permissions,
                    refreshHash);
            if (sessionStore.create(session, refreshHash, authProperties.refreshTokenTtl())) {
                return new CreatedSession(session, refreshToken);
            }
        }
    }

    private TokenResponse tokenResponse(String accessToken, String refreshToken) {
        return new TokenResponse(
                accessToken,
                refreshToken,
                BEARER_TOKEN_TYPE,
                authProperties.accessTokenTtl().toSeconds());
    }

    private static AuthenticatedUser toPrincipal(AuthSession session) {
        return new AuthenticatedUser(
                session.tenantId(),
                session.userId(),
                session.sessionId(),
                session.username(),
                session.permissions());
    }

    private static boolean hasSameIdentity(AuthSession session, AuthenticatedUser principal) {
        return session.tenantId() == principal.tenantId()
                && session.userId() == principal.userId()
                && session.sessionId().equals(principal.sessionId())
                && session.username().equals(principal.username());
    }

    /** 将已原子落库的会话与仅返回调用方的 Refresh Token 成对携带。 */
    private record CreatedSession(AuthSession session, String refreshToken) {
    }
}
