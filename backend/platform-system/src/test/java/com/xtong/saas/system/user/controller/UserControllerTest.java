package com.xtong.saas.system.user.controller;

import com.xtong.saas.common.exception.GlobalExceptionHandler;
import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.auth.config.SecurityConfig;
import com.xtong.saas.system.auth.filter.JwtAuthenticationFilter;
import com.xtong.saas.system.auth.handler.RestAccessDeniedHandler;
import com.xtong.saas.system.auth.handler.RestAuthenticationEntryPoint;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.session.SessionStore;
import com.xtong.saas.system.auth.service.SessionPrincipalValidator;
import com.xtong.saas.system.auth.token.AccessTokenService;
import com.xtong.saas.system.menu.controller.MenuController;
import com.xtong.saas.system.menu.service.MenuService;
import com.xtong.saas.system.role.controller.RoleController;
import com.xtong.saas.system.role.dto.UpdateRoleDTO;
import com.xtong.saas.system.role.service.RoleService;
import com.xtong.saas.system.user.dto.CreateUserDTO;
import com.xtong.saas.system.user.dto.ResetPasswordDTO;
import com.xtong.saas.system.user.dto.UpdateUserDTO;
import com.xtong.saas.system.user.dto.UserQueryDTO;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.service.UserService;
import com.xtong.saas.system.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 以独立 HTTP 契约矩阵验证 19 个系统管理端点的认证、授权、绑定和服务委派。 */
@SpringJUnitWebConfig(UserControllerTest.TestConfiguration.class)
@TestPropertySource(properties = {
        "saas.auth.jwt-secret=test-only-secret-with-at-least-32-bytes",
        "saas.auth.access-token-ttl=15m",
        "saas.auth.refresh-token-ttl=7d",
        "saas.auth.login-failure-limit=5",
        "saas.auth.login-failure-window=15m",
        "saas.auth.login-lock-duration=15m"
})
class UserControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final long TENANT_ID = 7L;
    private static final long CURRENT_USER_ID = 11L;

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private MenuService menuService;

    @MockitoBean
    private AccessTokenService accessTokenService;

    @MockitoBean
    private SessionStore sessionStore;

    @MockitoBean
    private SessionPrincipalValidator sessionPrincipalValidator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        when(sessionPrincipalValidator.isValid(any())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        when(userService.page(any())).thenReturn(new PageResult<>(List.of(), 0, 1, 20));
        when(userService.create(any())).thenReturn("101");
        when(roleService.page(any())).thenReturn(new PageResult<>(List.of(), 0, 1, 20));
        when(roleService.create(any())).thenReturn("201");
        when(menuService.getTree()).thenReturn(List.of());
        when(menuService.getPermissionCodes()).thenReturn(Set.of());
    }

    @ParameterizedTest(name = "匹配权限: {0}")
    @MethodSource("managementEndpointContract")
    void everyEndpointShouldAllowItsExactAuthority(EndpointContract endpoint) throws Exception {
        authenticate(endpoint.authority());

        mockMvc.perform(endpoint.request().header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @ParameterizedTest(name = "拒绝错配权限: {0}")
    @MethodSource("managementEndpointContract")
    void everyEndpointShouldRejectAnExplicitMismatchedAuthority(EndpointContract endpoint) throws Exception {
        authenticate("unrelated:authority");

        mockMvc.perform(endpoint.request().header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1102));
    }

    @ParameterizedTest(name = "拒绝匿名访问: {0}")
    @MethodSource("managementEndpointContract")
    void everyEndpointShouldRejectAnonymousRequest(EndpointContract endpoint) throws Exception {
        mockMvc.perform(endpoint.request())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1101));
    }

    @Test
    void userPageShouldRejectPageNumZero() throws Exception {
        authenticate("system:user:list");

        mockMvc.perform(get("/api/v1/system/users?pageNum=0&pageSize=20")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void userPageShouldAllowPageSize500AndReject501() throws Exception {
        authenticate("system:user:list");

        mockMvc.perform(get("/api/v1/system/users?pageNum=1&pageSize=500")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/system/users?pageNum=1&pageSize=501")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        verify(userService).page(new UserQueryDTO(1, 500, null, null));
    }

    @Test
    void nonNumericPathIdShouldReturnUnifiedInvalidParameter() throws Exception {
        authenticate("system:user:detail");

        mockMvc.perform(get("/api/v1/system/users/not-a-number")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @ParameterizedTest
    @MethodSource("invalidBodyContracts")
    void invalidBodiesShouldReturnUnifiedValidationFailure(EndpointContract endpoint) throws Exception {
        authenticate(endpoint.authority());

        mockMvc.perform(endpoint.request().header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void emptyRoleIdsShouldBeAllowedAndDelegatedInArgumentOrder() throws Exception {
        authenticate("system:user:assign-role");

        mockMvc.perform(put("/api/v1/system/users/7/roles")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).assignRoles(7L, Set.of());
    }

    @Test
    void emptyMenuIdsShouldBeAllowedAndDelegatedInArgumentOrder() throws Exception {
        authenticate("system:role:assign-menu");

        mockMvc.perform(put("/api/v1/system/roles/8/menus")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(roleService).assignMenus(8L, Set.of());
    }

    @Test
    void disableAndDeleteShouldUseTargetIdThenCurrentAuthenticatedUserId() throws Exception {
        authenticate(Set.of("system:user:disable", "system:user:delete"));

        mockMvc.perform(post("/api/v1/system/users/7/disable")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(HttpMethod.DELETE, "/api/v1/system/users/8")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());

        verify(userService).disable(7L, CURRENT_USER_ID);
        verify(userService).delete(8L, CURRENT_USER_ID);
    }

    @Test
    void updateAndResetShouldBindValidatedCommandsAndPreserveArgumentOrder() throws Exception {
        authenticate(Set.of("system:user:update", "system:user:reset-password", "system:role:update"));

        mockMvc.perform(put("/api/v1/system/users/7")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice\",\"email\":\"alice@example.com\",\"mobile\":\"13800000000\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/system/users/8/reset-password")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password1\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/system/roles/9")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"Operator\"}"))
                .andExpect(status().isOk());

        verify(userService).update(7L, new UpdateUserDTO("Alice", "alice@example.com", "13800000000"));
        verify(userService).resetPassword(8L, new ResetPasswordDTO("password1"));
        verify(roleService).update(9L, new UpdateRoleDTO("Operator"));
    }

    @Test
    void createShouldReturnStringIdAndDelegateTheValidatedCommand() throws Exception {
        authenticate("system:user:create");
        CreateUserDTO expected = new CreateUserDTO(
                "alice", "Alice", "password1", "alice@example.com", "13800000000", Set.of());

        mockMvc.perform(post("/api/v1/system/users")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUserCreateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("101"));

        verify(userService).create(expected);
    }

    @Test
    void detailShouldSerializeSnowflakeIdAsJsonString() throws Exception {
        authenticate("system:user:detail");
        when(userService.get(9_007_199_254_740_993L)).thenReturn(new UserVO(
                "9007199254740993", "7", "alice", "Alice", null, null,
                UserStatus.ENABLED, List.of(), null, null, null, null));

        mockMvc.perform(get("/api/v1/system/users/9007199254740993")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("9007199254740993"));

        verify(userService).get(9_007_199_254_740_993L);
    }

    private void authenticate(String authority) {
        authenticate(Set.of(authority));
    }

    private void authenticate(Set<String> authorities) {
        AuthenticatedUser claims = new AuthenticatedUser(
                TENANT_ID, CURRENT_USER_ID, "session-1", "alice", authorities);
        AuthSession session = new AuthSession(
                "session-1", TENANT_ID, CURRENT_USER_ID, "alice", "Alice", authorities, "hash");
        when(accessTokenService.parse(VALID_TOKEN)).thenReturn(claims);
        when(sessionStore.find("session-1")).thenReturn(Optional.of(session));
    }

    private static Stream<EndpointContract> managementEndpointContract() {
        return Stream.of(
                endpoint(HttpMethod.GET, "/api/v1/system/users?pageNum=1&pageSize=20", null, "system:user:list"),
                endpoint(HttpMethod.GET, "/api/v1/system/users/1", null, "system:user:detail"),
                endpoint(HttpMethod.POST, "/api/v1/system/users", validUserCreateBody(), "system:user:create"),
                endpoint(HttpMethod.PUT, "/api/v1/system/users/1", validUserUpdateBody(), "system:user:update"),
                endpoint(HttpMethod.POST, "/api/v1/system/users/1/enable", null, "system:user:enable"),
                endpoint(HttpMethod.POST, "/api/v1/system/users/1/disable", null, "system:user:disable"),
                endpoint(HttpMethod.POST, "/api/v1/system/users/1/reset-password", "{\"password\":\"password1\"}", "system:user:reset-password"),
                endpoint(HttpMethod.DELETE, "/api/v1/system/users/1", null, "system:user:delete"),
                endpoint(HttpMethod.PUT, "/api/v1/system/users/1/roles", "{\"roleIds\":[]}", "system:user:assign-role"),
                endpoint(HttpMethod.GET, "/api/v1/system/roles?pageNum=1&pageSize=20", null, "system:role:list"),
                endpoint(HttpMethod.GET, "/api/v1/system/roles/1", null, "system:role:detail"),
                endpoint(HttpMethod.POST, "/api/v1/system/roles", "{\"roleCode\":\"OPERATOR\",\"roleName\":\"Operator\"}", "system:role:create"),
                endpoint(HttpMethod.PUT, "/api/v1/system/roles/1", "{\"roleName\":\"Operator\"}", "system:role:update"),
                endpoint(HttpMethod.POST, "/api/v1/system/roles/1/enable", null, "system:role:enable"),
                endpoint(HttpMethod.POST, "/api/v1/system/roles/1/disable", null, "system:role:disable"),
                endpoint(HttpMethod.DELETE, "/api/v1/system/roles/1", null, "system:role:delete"),
                endpoint(HttpMethod.PUT, "/api/v1/system/roles/1/menus", "{\"menuIds\":[]}", "system:role:assign-menu"),
                endpoint(HttpMethod.GET, "/api/v1/system/menus/tree", null, "system:menu:tree"),
                endpoint(HttpMethod.GET, "/api/v1/system/menus/permissions", null, "system:permission:list"));
    }

    private static Stream<EndpointContract> invalidBodyContracts() {
        return Stream.of(
                endpoint(HttpMethod.POST, "/api/v1/system/users", "{}", "system:user:create"),
                endpoint(HttpMethod.PUT, "/api/v1/system/users/1", "{\"displayName\":\"\"}", "system:user:update"),
                endpoint(HttpMethod.POST, "/api/v1/system/users/1/reset-password", "{\"password\":\"short\"}", "system:user:reset-password"),
                endpoint(HttpMethod.POST, "/api/v1/system/roles", "{\"roleCode\":\"\",\"roleName\":\"\"}", "system:role:create"),
                endpoint(HttpMethod.PUT, "/api/v1/system/roles/1", "{\"roleName\":\"\"}", "system:role:update"));
    }

    private static EndpointContract endpoint(HttpMethod method, String path, String body, String authority) {
        return new EndpointContract(method, path, body, authority);
    }

    private static String validUserCreateBody() {
        return "{\"username\":\"alice\",\"displayName\":\"Alice\",\"password\":\"password1\","
                + "\"email\":\"alice@example.com\",\"mobile\":\"13800000000\",\"roleIds\":[]}";
    }

    private static String validUserUpdateBody() {
        return "{\"displayName\":\"Alice\",\"email\":\"alice@example.com\",\"mobile\":\"13800000000\"}";
    }

    /** 表示由设计文档独立硬编码的一行管理端点 HTTP 与权限契约。 */
    private record EndpointContract(HttpMethod method, String path, String body, String authority) {

        private MockHttpServletRequestBuilder request() {
            MockHttpServletRequestBuilder builder =
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, path);
            if (body != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return builder;
        }

        @Override
        public String toString() {
            return method + " " + path + " -> " + authority;
        }
    }

    /** 提供包含全部三个管理 Controller 的真实 MVC、JWT 过滤器和方法安全测试上下文。 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            UserController.class,
            RoleController.class,
            MenuController.class,
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class,
            GlobalExceptionHandler.class
    })
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
