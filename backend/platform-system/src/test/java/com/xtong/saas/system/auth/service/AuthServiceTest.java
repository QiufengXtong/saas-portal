package com.xtong.saas.system.auth.service;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.exception.CommonErrorCode;
import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.dto.LoginRequest;
import com.xtong.saas.system.auth.dto.RefreshTokenRequest;
import com.xtong.saas.system.auth.exception.AuthErrorCode;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.service.impl.AuthServiceImpl;
import com.xtong.saas.system.auth.session.LoginFailureService;
import com.xtong.saas.system.auth.session.SessionIdGenerator;
import com.xtong.saas.system.auth.session.SessionStore;
import com.xtong.saas.system.auth.token.AccessTokenService;
import com.xtong.saas.system.auth.token.RefreshTokenGenerator;
import com.xtong.saas.system.auth.token.TokenHashService;
import com.xtong.saas.system.auth.vo.CurrentUserVO;
import com.xtong.saas.system.auth.vo.TokenResponse;
import com.xtong.saas.system.menu.service.PermissionService;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.tenant.enums.TenantStatus;
import com.xtong.saas.system.tenant.exception.TenantErrorCode;
import com.xtong.saas.system.tenant.service.TenantService;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.exception.UserErrorCode;
import com.xtong.saas.system.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证登录、原子刷新、退出和当前用户查询的认证用例编排。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    @Mock
    private TenantService tenantService;
    @Mock
    private UserService userService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginFailureService loginFailureService;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private SessionIdGenerator sessionIdGenerator;
    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;
    @Mock
    private TokenHashService tokenHashService;
    @Mock
    private AccessTokenService accessTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                "test-only-secret-with-at-least-32-bytes",
                Duration.ofMinutes(15),
                REFRESH_TTL,
                5,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15));
        authService = new AuthServiceImpl(
                tenantService,
                userService,
                permissionService,
                passwordEncoder,
                loginFailureService,
                sessionStore,
                sessionIdGenerator,
                refreshTokenGenerator,
                tokenHashService,
                accessTokenService,
                properties);
    }

    @Test
    void shouldLoginInRequiredOrderAndCreatePermissionBackedSession() {
        SystemTenant tenant = enabledTenant(11L);
        SystemUser user = enabledUser(22L, "admin", "System Admin", "encoded");
        when(tenantService.requireEnabledByCode("default")).thenReturn(tenant);
        when(userService.requireEnabledForLogin(11L, "admin")).thenReturn(user);
        when(passwordEncoder.matches(" Secret123 ", "encoded")).thenReturn(true);
        when(permissionService.loadUserPermissions(11L, 22L)).thenReturn(Set.of("system:user:list"));
        when(sessionIdGenerator.generate()).thenReturn("session-1");
        when(refreshTokenGenerator.generate()).thenReturn("refresh-1");
        when(tokenHashService.hash("refresh-1")).thenReturn("refresh-hash-1");
        when(sessionStore.create(any(AuthSession.class), eq("refresh-hash-1"), eq(REFRESH_TTL)))
                .thenReturn(true);
        when(accessTokenService.issue(any(AuthenticatedUser.class))).thenReturn("access-1");

        TokenResponse response = authService.login(new LoginRequest(" Default ", " ADMIN ", " Secret123 "));

        assertThat(response).isEqualTo(new TokenResponse("access-1", "refresh-1", "Bearer", 900L));
        InOrder order = inOrder(tenantService, loginFailureService, userService, passwordEncoder, permissionService);
        order.verify(loginFailureService).assertAllowed("default", "admin");
        order.verify(tenantService).requireEnabledByCode("default");
        order.verify(tenantService).lockAndRequireEnabled(11L, "default");
        order.verify(loginFailureService).assertAllowed("default", "admin");
        order.verify(userService).requireEnabledForLogin(11L, "admin");
        order.verify(passwordEncoder).matches(" Secret123 ", "encoded");
        order.verify(permissionService).loadUserPermissions(11L, 22L);
        verify(loginFailureService).clear("default", "admin");
        verify(userService).recordLoginSuccess(eq(22L), any());
        verify(sessionStore).create(
                eq(new AuthSession("session-1", 11L, 22L, "admin", "System Admin",
                        Set.of("system:user:list"), "refresh-hash-1")),
                eq("refresh-hash-1"),
                eq(REFRESH_TTL));
    }

    @Test
    void shouldStopKnownTenantAttemptWhenSecondFailureCheckIsLocked() {
        when(tenantService.requireEnabledByCode("default")).thenReturn(enabledTenant(11L));
        org.mockito.Mockito.doNothing()
                .doThrow(new BusinessException(AuthErrorCode.LOGIN_LOCKED))
                .when(loginFailureService).assertAllowed("default", "admin");

        assertThatThrownBy(() -> authService.login(new LoginRequest("default", "admin", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.LOGIN_LOCKED);

        InOrder order = inOrder(loginFailureService, tenantService);
        order.verify(loginFailureService).assertAllowed("default", "admin");
        order.verify(tenantService).requireEnabledByCode("default");
        order.verify(tenantService).lockAndRequireEnabled(11L, "default");
        order.verify(loginFailureService).assertAllowed("default", "admin");
        verifyNoInteractions(userService, passwordEncoder, permissionService, sessionStore);
    }

    @Test
    void shouldRetryWholeCredentialSetWhenSessionIdentifierCollides() {
        stubSuccessfulIdentity();
        when(sessionIdGenerator.generate()).thenReturn("collision", "session-2");
        when(refreshTokenGenerator.generate()).thenReturn("refresh-1", "refresh-2");
        when(tokenHashService.hash("refresh-1")).thenReturn("hash-1");
        when(tokenHashService.hash("refresh-2")).thenReturn("hash-2");
        when(sessionStore.create(any(AuthSession.class), eq("hash-1"), eq(REFRESH_TTL))).thenReturn(false);
        when(sessionStore.create(any(AuthSession.class), eq("hash-2"), eq(REFRESH_TTL))).thenReturn(true);
        when(accessTokenService.issue(any())).thenReturn("access-2");

        TokenResponse response = authService.login(new LoginRequest("default", "admin", "Secret123"));

        assertThat(response.refreshToken()).isEqualTo("refresh-2");
        verify(sessionStore).create(any(AuthSession.class), eq("hash-1"), eq(REFRESH_TTL));
        verify(sessionStore).create(any(AuthSession.class), eq("hash-2"), eq(REFRESH_TTL));
    }

    @Test
    void shouldCompensateCreatedRedisSessionWhenLoginTransactionRollsBack() {
        stubSuccessfulIdentity();
        when(sessionIdGenerator.generate()).thenReturn("session-rollback");
        when(refreshTokenGenerator.generate()).thenReturn("refresh-rollback");
        when(tokenHashService.hash("refresh-rollback")).thenReturn("hash-rollback");
        when(sessionStore.create(any(), eq("hash-rollback"), eq(REFRESH_TTL))).thenReturn(true);
        when(accessTokenService.issue(any())).thenReturn("access");
        TransactionSynchronizationManager.initSynchronization();

        authService.login(new LoginRequest("default", "admin", "Secret123"));
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();

        verify(sessionStore).delete("session-rollback");
    }

    @Test
    void shouldStopAfterThreeSessionCredentialCollisions() {
        stubSuccessfulIdentity();
        when(sessionIdGenerator.generate()).thenReturn("session-1", "session-2", "session-3");
        when(refreshTokenGenerator.generate()).thenReturn("refresh-1", "refresh-2", "refresh-3");
        when(tokenHashService.hash("refresh-1")).thenReturn("hash-1");
        when(tokenHashService.hash("refresh-2")).thenReturn("hash-2");
        when(tokenHashService.hash("refresh-3")).thenReturn("hash-3");
        when(sessionStore.create(any(AuthSession.class), any(), eq(REFRESH_TTL))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("default", "admin", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
        verify(sessionStore, times(3)).create(any(AuthSession.class), any(), eq(REFRESH_TTL));
        verifyNoInteractions(accessTokenService);
    }

    @Test
    void shouldHideUnknownUserBehindInvalidCredentialsAndRecordFailure() {
        when(tenantService.requireEnabledByCode("default")).thenReturn(enabledTenant(11L));
        when(userService.requireEnabledForLogin(11L, "missing"))
                .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));
        when(passwordEncoder.matches(eq("Secret123"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("default", "missing", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginFailureService).recordFailure("default", "missing");
        verify(passwordEncoder).matches(eq("Secret123"), any());
        verifyNoInteractions(permissionService, sessionStore);
    }

    @Test
    void shouldHideUnknownTenantBehindInvalidCredentials() {
        when(tenantService.requireEnabledByCode("missing"))
                .thenThrow(new BusinessException(TenantErrorCode.TENANT_NOT_FOUND));
        when(passwordEncoder.matches(eq("Secret123"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "admin", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        InOrder order = inOrder(loginFailureService, tenantService, passwordEncoder);
        order.verify(loginFailureService).assertAllowed("missing", "admin");
        order.verify(tenantService).requireEnabledByCode("missing");
        order.verify(passwordEncoder).matches(eq("Secret123"), any());
        verify(loginFailureService).recordFailure("missing", "admin");
        verifyNoInteractions(userService, permissionService, sessionStore);
    }

    @Test
    void shouldAccumulateUnknownTenantFailureAndLockBeforeSecondLookup() {
        when(tenantService.requireEnabledByCode("missing"))
                .thenThrow(new BusinessException(TenantErrorCode.TENANT_NOT_FOUND));
        when(passwordEncoder.matches(eq("Secret123"), any())).thenReturn(false);
        org.mockito.Mockito.doNothing()
                .doThrow(new BusinessException(AuthErrorCode.LOGIN_LOCKED))
                .when(loginFailureService).assertAllowed("missing", "admin");

        assertThatThrownBy(() -> authService.login(new LoginRequest(" Missing ", " ADMIN ", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "admin", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.LOGIN_LOCKED);
        verify(tenantService, times(1)).requireEnabledByCode("missing");
        verify(loginFailureService, times(1)).recordFailure("missing", "admin");
        verify(passwordEncoder, times(1)).matches(eq("Secret123"), any());
    }

    @Test
    void shouldHideWrongPasswordBehindSameInvalidCredentialsAndRecordFailure() {
        when(tenantService.requireEnabledByCode("default")).thenReturn(enabledTenant(11L));
        when(userService.requireEnabledForLogin(11L, "admin"))
                .thenReturn(enabledUser(22L, "admin", "Admin", "encoded"));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("default", "admin", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginFailureService).recordFailure("default", "admin");
        verifyNoInteractions(permissionService, sessionStore);
    }

    @Test
    void shouldHideDisabledTenantAndUserBehindInvalidCredentialsWithDummyPasswordWork() {
        when(tenantService.requireEnabledByCode("disabled"))
                .thenThrow(new BusinessException(TenantErrorCode.TENANT_DISABLED));
        when(passwordEncoder.matches(eq("Secret123"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("disabled", "admin", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginFailureService).recordFailure("disabled", "admin");
        verify(passwordEncoder).matches(eq("Secret123"), any());

        when(tenantService.requireEnabledByCode("default")).thenReturn(enabledTenant(11L));
        when(userService.requireEnabledForLogin(11L, "disabled"))
                .thenThrow(new BusinessException(UserErrorCode.USER_DISABLED));

        assertThatThrownBy(() -> authService.login(new LoginRequest("default", "disabled", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginFailureService).recordFailure("default", "disabled");
        verify(passwordEncoder, times(2)).matches(eq("Secret123"), any());
    }

    @Test
    void shouldStopLockedLoginBeforeLookingUpUser() {
        org.mockito.Mockito.doThrow(new BusinessException(AuthErrorCode.LOGIN_LOCKED))
                .when(loginFailureService).assertAllowed("default", "admin");

        assertThatThrownBy(() -> authService.login(new LoginRequest("default", "admin", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.LOGIN_LOCKED);
        verifyNoInteractions(tenantService, userService, passwordEncoder, permissionService, sessionStore);
    }

    @Test
    void shouldRejectInvalidIdentityWithoutDatabaseOrFailureKeyAccess() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("acme:evil", "admín", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(tenantService, userService, loginFailureService, sessionStore);
    }

    @Test
    void loginAndRefreshShouldDeclareRealTransactionalBoundaries() throws Exception {
        assertThat(AuthServiceImpl.class.getMethod("login", LoginRequest.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
        assertThat(AuthServiceImpl.class.getMethod("refresh", RefreshTokenRequest.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
    }

    @Test
    void shouldRejectRefreshWhenDurableAuthenticationVersionChanged() {
        AuthSession oldSession = new AuthSession(
                "session-1", 11L, 22L, "admin", "Admin", Set.of(), 4L, "old-hash");
        SystemUser currentUser = enabledUser(22L, "admin", "Admin", "encoded");
        currentUser.setAuthVersion(5L);
        when(tokenHashService.hash("old-refresh")).thenReturn("old-hash");
        when(sessionStore.peekRefreshSession("old-hash")).thenReturn(Optional.of(oldSession));
        when(userService.requireEnabledForSession(11L, 22L)).thenReturn(currentUser);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("old-refresh")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);

        verify(tenantService).lockAndRequireEnabled(11L);
        verify(sessionStore).delete("session-1");
        verify(sessionStore, never()).rotateRefreshToken(any(), any(), any());
    }

    @Test
    void shouldUseAtomicTaskNineRotationAndIssueTokensFromRotatedSession() {
        AuthSession rotated = new AuthSession(
                "session-1", 11L, 22L, "admin", "Admin", Set.of("system:user:list"), "new-hash");
        SystemUser currentUser = enabledUser(22L, "admin", "Admin", "encoded");
        currentUser.setAuthVersion(0L);
        when(tokenHashService.hash("old-refresh")).thenReturn("old-hash");
        when(refreshTokenGenerator.generate()).thenReturn("new-refresh");
        when(tokenHashService.hash("new-refresh")).thenReturn("new-hash");
        when(sessionStore.peekRefreshSession("old-hash")).thenReturn(Optional.of(rotated));
        when(userService.requireEnabledForSession(11L, 22L)).thenReturn(currentUser);
        when(sessionStore.rotateRefreshToken("old-hash", "new-hash", REFRESH_TTL))
                .thenReturn(Optional.of(rotated));
        when(accessTokenService.issue(any())).thenReturn("new-access");

        TokenResponse response = authService.refresh(new RefreshTokenRequest("old-refresh"));

        assertThat(response).isEqualTo(new TokenResponse("new-access", "new-refresh", "Bearer", 900L));
        verify(sessionStore).rotateRefreshToken("old-hash", "new-hash", REFRESH_TTL);
        verify(accessTokenService).issue(new AuthenticatedUser(
                11L, 22L, "session-1", "admin", Set.of("system:user:list")));
    }

    @Test
    void shouldRejectRefreshReplayWithoutRestoringOldToken() {
        when(tokenHashService.hash("replayed-refresh")).thenReturn("old-hash");

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("replayed-refresh")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        verify(sessionStore).peekRefreshSession("old-hash");
        verifyNoInteractions(accessTokenService);
    }

    @Test
    void shouldDeleteOnlyCurrentSessionOnLogout() {
        authService.logout("session-1");

        verify(sessionStore).delete("session-1");
        verify(sessionStore, never()).deleteAll(anyLong(), anyLong());
    }

    @Test
    void shouldBuildCurrentUserFromLiveSession() {
        AuthenticatedUser principal = new AuthenticatedUser(
                11L, 22L, "session-1", "admin", Set.of("old-permission"));
        when(sessionStore.find("session-1")).thenReturn(Optional.of(new AuthSession(
                "session-1", 11L, 22L, "admin", "System Admin", Set.of("system:user:list"), "hash")));

        CurrentUserVO currentUser = authService.currentUser(principal);

        assertThat(currentUser).isEqualTo(new CurrentUserVO(
                "11", "22", "admin", "System Admin", Set.of("system:user:list")));
    }

    @Test
    void shouldRejectCurrentUserWhenSessionNoLongerExists() {
        AuthenticatedUser principal = new AuthenticatedUser(11L, 22L, "gone", "admin", Set.of());
        when(sessionStore.find("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentUser(principal))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.SESSION_EXPIRED);
    }

    @Test
    void shouldRejectLoginPasswordBeyondBcryptUtf8LimitAtServiceBoundary() {
        LoginRequest request = new LoginRequest("default", "admin", "密".repeat(25));
        when(tenantService.requireEnabledByCode("default")).thenReturn(enabledTenant(11L));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginFailureService, times(2)).assertAllowed("default", "admin");
        verify(loginFailureService).recordFailure("default", "admin");
        verify(tenantService).lockAndRequireEnabled(11L, "default");
        verify(passwordEncoder).matches(eq("invalid-password"), any());
        verifyNoInteractions(userService, permissionService, sessionStore);
    }

    @Test
    void shouldRejectEmptyLoginPasswordAtServiceBoundary() {
        when(tenantService.requireEnabledByCode("default")).thenReturn(enabledTenant(11L));

        assertThatThrownBy(() -> authService.login(new LoginRequest("default", "admin", "")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginFailureService, times(2)).assertAllowed("default", "admin");
        verify(loginFailureService).recordFailure("default", "admin");
        verify(tenantService).lockAndRequireEnabled(11L, "default");
        verify(passwordEncoder).matches(eq("invalid-password"), any());
        verifyNoInteractions(userService, permissionService, sessionStore);
    }

    private void stubSuccessfulIdentity() {
        when(tenantService.requireEnabledByCode("default")).thenReturn(enabledTenant(11L));
        when(userService.requireEnabledForLogin(11L, "admin"))
                .thenReturn(enabledUser(22L, "admin", "Admin", "encoded"));
        when(passwordEncoder.matches("Secret123", "encoded")).thenReturn(true);
        when(permissionService.loadUserPermissions(11L, 22L)).thenReturn(Set.of());
    }

    private static SystemTenant enabledTenant(long tenantId) {
        SystemTenant tenant = new SystemTenant();
        tenant.setId(tenantId);
        tenant.setTenantCode("default");
        tenant.setTenantName("Default Tenant");
        tenant.setStatus(TenantStatus.ENABLED);
        return tenant;
    }

    private static SystemUser enabledUser(long userId, String username, String displayName, String passwordHash) {
        SystemUser user = new SystemUser();
        user.setId(userId);
        user.setTenantId(11L);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordHash);
        user.setStatus(UserStatus.ENABLED);
        return user;
    }
}
