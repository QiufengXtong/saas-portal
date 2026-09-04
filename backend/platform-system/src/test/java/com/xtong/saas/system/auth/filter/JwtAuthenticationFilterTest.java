package com.xtong.saas.system.auth.filter;

import com.xtong.saas.system.auth.handler.RestAuthenticationEntryPoint;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.session.SessionStore;
import com.xtong.saas.system.auth.service.SecurityAuditorProvider;
import com.xtong.saas.system.auth.token.AccessTokenService;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.tenant.context.TenantScope;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证 JWT 请求认证、Redis 会话身份绑定以及线程上下文清理边界。 */
class JwtAuthenticationFilterTest {

    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            accessTokenService,
            sessionStore,
            new RestAuthenticationEntryPoint(new ObjectMapper()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseLatestSessionPermissionsAndClearContextsAfterFilterChain() throws Exception {
        AuthenticatedUser tokenClaims = new AuthenticatedUser(7L, 11L, "session-1", "alice", Set.of());
        AuthSession session = new AuthSession(
                "session-1", 7L, 11L, "alice", "Alice", Set.of("system:user:list"), "hash");
        when(accessTokenService.parse("valid-token")).thenReturn(tokenClaims);
        when(sessionStore.find("session-1")).thenReturn(Optional.of(session));
        MockHttpServletRequest request = requestWithAuthorization("Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(TenantContextHolder.requireTenantId()).isEqualTo(7L);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(new AuthenticatedUser(
                            7L, 11L, "session-1", "alice", Set.of("system:user:list")));
            assertThat(new SecurityAuditorProvider().currentAuditorId()).hasValue(11L);
        };

        TenantScope.run(99L, () -> {
            invokeFilter(filter, request, response, chain);
            assertThat(TenantContextHolder.currentTenantId()).isEmpty();
        });

        assertThat(TenantContextHolder.currentTenantId()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldContinueWithoutAttemptingAuthenticationWhenBearerHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = {false};

        filter.doFilter(request, response, (servletRequest, servletResponse) -> invoked[0] = true);

        assertThat(invoked[0]).isTrue();
        verifyNoInteractions(accessTokenService, sessionStore);
    }

    @Test
    void auditorShouldBeEmptyWithoutAuthenticatedSystemPrincipal() {
        assertThat(new SecurityAuditorProvider().currentAuditorId()).isEmpty();
    }

    @Test
    void shouldReturnUnifiedUnauthorizedWhenBearerFormatIsInvalid() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                requestWithAuthorization("Token invalid"),
                response,
                (servletRequest, servletResponse) -> {
                    throw new AssertionError("invalid bearer must not reach the filter chain");
                });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":1101");
    }

    @Test
    void shouldReturnUnifiedUnauthorizedWhenTokenValidationFails() throws Exception {
        when(accessTokenService.parse("expired-token")).thenThrow(new BadJwtException("expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                requestWithAuthorization("Bearer expired-token"),
                response,
                (servletRequest, servletResponse) -> {
                    throw new AssertionError("invalid token must not reach the filter chain");
                });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":1101");
    }

    @Test
    void shouldReturnUnifiedUnauthorizedWhenSessionIdentityDoesNotMatchToken() throws Exception {
        when(accessTokenService.parse("valid-token"))
                .thenReturn(new AuthenticatedUser(7L, 11L, "session-1", "alice", Set.of()));
        when(sessionStore.find("session-1")).thenReturn(Optional.of(
                new AuthSession("session-1", 8L, 11L, "alice", "Alice", Set.of(), "hash")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                requestWithAuthorization("Bearer valid-token"),
                response,
                (servletRequest, servletResponse) -> {
                    throw new AssertionError("mismatched session must not reach the filter chain");
                });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":1101");
        assertThat(TenantContextHolder.currentTenantId()).isEmpty();
    }

    @Test
    void shouldReturnUnifiedUnauthorizedWhenSessionIsMissing() throws Exception {
        when(accessTokenService.parse("valid-token"))
                .thenReturn(new AuthenticatedUser(7L, 11L, "session-1", "alice", Set.of()));
        when(sessionStore.find("session-1")).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                requestWithAuthorization("Bearer valid-token"),
                response,
                (servletRequest, servletResponse) -> {
                    throw new AssertionError("missing session must not reach the filter chain");
                });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":1101");
    }

    private static MockHttpServletRequest requestWithAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", value);
        return request;
    }

    private static void invokeFilter(
            JwtAuthenticationFilter filter,
            MockHttpServletRequest request,
            MockHttpServletResponse response,
            FilterChain chain) {
        try {
            filter.doFilter(request, response, chain);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
