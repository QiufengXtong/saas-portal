package com.xtong.saas.system.auth.controller;

import com.xtong.saas.common.exception.GlobalExceptionHandler;
import com.xtong.saas.system.auth.config.SecurityConfig;
import com.xtong.saas.system.auth.filter.JwtAuthenticationFilter;
import com.xtong.saas.system.auth.handler.RestAccessDeniedHandler;
import com.xtong.saas.system.auth.handler.RestAuthenticationEntryPoint;
import com.xtong.saas.system.auth.dto.LoginRequest;
import com.xtong.saas.system.auth.dto.RefreshTokenRequest;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.service.AuthService;
import com.xtong.saas.system.auth.service.SessionPrincipalValidator;
import com.xtong.saas.system.auth.session.SessionStore;
import com.xtong.saas.system.auth.token.AccessTokenService;
import com.xtong.saas.system.auth.vo.CurrentUserVO;
import com.xtong.saas.system.auth.vo.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证认证 HTTP API 的匿名边界、请求校验和受信主体参数传递。 */
@SpringJUnitWebConfig(AuthControllerTest.TestConfiguration.class)
@TestPropertySource(properties = {
        "saas.auth.jwt-secret=test-only-secret-with-at-least-32-bytes",
        "saas.auth.access-token-ttl=15m",
        "saas.auth.refresh-token-ttl=7d",
        "saas.auth.login-failure-limit=5",
        "saas.auth.login-failure-window=15m",
        "saas.auth.login-lock-duration=15m"
})
class AuthControllerTest {

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccessTokenService accessTokenService;

    @MockitoBean
    private SessionStore sessionStore;

    @MockitoBean
    private SessionPrincipalValidator sessionPrincipalValidator;

    @BeforeEach
    void setUpMockMvc() {
        when(sessionPrincipalValidator.isValid(any())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturnUnified401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1101));
    }

    @Test
    void loginShouldValidateRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"\",\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void loginShouldBeAnonymousAndDelegateValidatedRequest() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new TokenResponse("access", "refresh", "Bearer", 900L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"ACME\",\"username\":\"Admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"));

        verify(authService).login(new LoginRequest("ACME", "Admin", "password"));
    }

    @Test
    void refreshShouldBeAnonymousAndDelegateValidatedRequest() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenReturn(new TokenResponse("new-access", "new-refresh", "Bearer", 900L));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));

        verify(authService).refresh(new RefreshTokenRequest("refresh-token"));
    }

    @Test
    void meShouldUseAuthenticatedPrincipal() throws Exception {
        AuthenticatedUser tokenClaims = new AuthenticatedUser(7L, 11L, "session-1", "alice", Set.of());
        AuthSession session = new AuthSession(
                "session-1", 7L, 11L, "alice", "Alice", Set.of("system:user:list"), "hash");
        when(accessTokenService.parse("valid-token")).thenReturn(tokenClaims);
        when(sessionStore.find("session-1")).thenReturn(Optional.of(session));
        when(authService.currentUser(any(AuthenticatedUser.class))).thenReturn(
                new CurrentUserVO("7", "11", "alice", "Alice", Set.of("system:user:list")));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("11"));

        verify(authService).currentUser(new AuthenticatedUser(
                7L, 11L, "session-1", "alice", Set.of("system:user:list")));
    }

    @Test
    void logoutShouldUseSessionIdFromAuthenticatedPrincipal() throws Exception {
        AuthenticatedUser tokenClaims = new AuthenticatedUser(7L, 11L, "session-1", "alice", Set.of());
        AuthSession session = new AuthSession("session-1", 7L, 11L, "alice", "Alice", Set.of(), "hash");
        when(accessTokenService.parse("valid-token")).thenReturn(tokenClaims);
        when(sessionStore.find("session-1")).thenReturn(Optional.of(session));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(authService).logout("session-1");
    }

    @Test
    void authenticatedRequestWithoutRequiredAuthorityShouldReturnUnified403() throws Exception {
        AuthenticatedUser tokenClaims = new AuthenticatedUser(7L, 11L, "session-1", "alice", Set.of());
        AuthSession session = new AuthSession("session-1", 7L, 11L, "alice", "Alice", Set.of(), "hash");
        when(accessTokenService.parse("valid-token")).thenReturn(tokenClaims);
        when(sessionStore.find("session-1")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/test/permission").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1102));
    }

    /** 提供不依赖 Boot 切片自动配置的最小 MVC 与 Security 测试上下文。 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            AuthController.class,
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class,
            GlobalExceptionHandler.class,
            PermissionProbeController.class
    })
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** 提供仅用于验证方法权限拒绝响应的受保护测试端点。 */
    @RestController
    static class PermissionProbeController {

        @GetMapping("/test/permission")
        @PreAuthorize("hasAuthority('test:required')")
        String requirePermission() {
            return "ok";
        }
    }
}
