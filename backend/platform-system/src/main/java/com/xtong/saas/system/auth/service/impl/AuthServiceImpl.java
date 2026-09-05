package com.xtong.saas.system.auth.service.impl;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.exception.CommonErrorCode;
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
import com.xtong.saas.system.identity.IdentityNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 编排租户登录、一次性 Refresh Token 轮换和会话读取，不暴露账号存在性。 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String BEARER_TOKEN_TYPE = "Bearer";
    private static final int SESSION_CREATION_ATTEMPTS = 3;
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

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
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse login(LoginRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String tenantCode = IdentityNormalizer.normalizeForLogin(request.tenantCode())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
        String username = IdentityNormalizer.normalizeForLogin(request.username())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
        loginFailureService.assertAllowed(tenantCode, username);
        if (!isBcryptPasswordLength(request.password())) {
            throw invalidCredentials(tenantCode, username);
        }
        SystemTenant tenant = requireLoginTenant(tenantCode, username, request.password());
        tenantService.lockAndRequireEnabled(tenant.getId(), tenantCode);
        CreatedSession created = TenantScope.call(tenant.getId(), () -> {
            SystemUser user = requireLoginUser(tenant.getId(), tenantCode, username, request.password());
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw invalidCredentials(tenantCode, username);
            }
            Set<String> permissions = permissionService.loadUserPermissions(tenant.getId(), user.getId());
            CreatedSession result = createUniqueSession(tenant.getId(), user, permissions);
            registerRollbackCompensation(result.session().sessionId());
            userService.recordLoginSuccess(user.getId(), LocalDateTime.now());
            return result;
        });
        String accessToken = accessTokenService.issue(toPrincipal(created.session()));
        loginFailureService.clear(tenantCode, username);
        return tokenResponse(accessToken, created.refreshToken());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse refresh(RefreshTokenRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String currentHash = tokenHashService.hash(request.refreshToken());
        AuthSession current = sessionStore.peekRefreshSession(currentHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        try {
            tenantService.lockAndRequireEnabled(current.tenantId());
        } catch (BusinessException exception) {
            sessionStore.delete(current.sessionId());
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        RotatedRefresh rotated = TenantScope.call(current.tenantId(), () -> {
            SystemUser user;
            try {
                user = userService.requireEnabledForSession(current.tenantId(), current.userId());
            } catch (BusinessException exception) {
                sessionStore.delete(current.sessionId());
                throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            long currentVersion = user.getAuthVersion() == null ? 0L : user.getAuthVersion();
            if (currentVersion != current.authVersion()) {
                sessionStore.delete(current.sessionId());
                throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            String newRefreshToken = refreshTokenGenerator.generate();
            String newHash = tokenHashService.hash(newRefreshToken);
            AuthSession result = sessionStore.rotateRefreshToken(
                            currentHash, newHash, authProperties.refreshTokenTtl())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
            registerRollbackCompensation(result.sessionId());
            return new RotatedRefresh(result, newRefreshToken);
        });
        String accessToken = accessTokenService.issue(toPrincipal(rotated.session()));
        return tokenResponse(accessToken, rotated.refreshToken());
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

    private SystemUser requireLoginUser(
            long tenantId, String tenantCode, String username, String password) {
        try {
            return userService.requireEnabledForLogin(tenantId, username);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != UserErrorCode.USER_NOT_FOUND
                    && exception.getErrorCode() != UserErrorCode.USER_DISABLED) {
                throw exception;
            }
            runDummyPasswordCheck(password);
            throw invalidCredentials(tenantCode, username);
        }
    }

    private SystemTenant requireLoginTenant(String tenantCode, String username, String password) {
        try {
            return tenantService.requireEnabledByCode(tenantCode);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == TenantErrorCode.TENANT_NOT_FOUND
                    || exception.getErrorCode() == TenantErrorCode.TENANT_DISABLED) {
                runDummyPasswordCheck(password);
                throw invalidCredentials(tenantCode, username);
            }
            throw exception;
        }
    }

    private CreatedSession createUniqueSession(long tenantId, SystemUser user, Set<String> permissions) {
        for (int attempt = 0; attempt < SESSION_CREATION_ATTEMPTS; attempt++) {
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
                    user.getAuthVersion() == null ? 0L : user.getAuthVersion(),
                    refreshHash);
            if (sessionStore.create(session, refreshHash, authProperties.refreshTokenTtl())) {
                return new CreatedSession(session, refreshToken);
            }
        }
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
    }

    private void runDummyPasswordCheck(String password) {
        passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
    }

    private BusinessException invalidCredentials(String tenantCode, String username) {
        loginFailureService.recordFailure(tenantCode, username);
        return new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    private static boolean isBcryptPasswordLength(String password) {
        return password != null
                && !password.isEmpty()
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
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

    private void registerRollbackCompensation(String sessionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    sessionStore.delete(sessionId);
                }
            }
        });
    }

    /** 将已原子落库的会话与仅返回调用方的 Refresh Token 成对携带。 */
    private record CreatedSession(AuthSession session, String refreshToken) {
    }

    /** 将已轮换会话与仅返回调用方的新 Refresh Token 配对，避免明文进入会话模型。 */
    private record RotatedRefresh(AuthSession session, String refreshToken) {
    }
}
