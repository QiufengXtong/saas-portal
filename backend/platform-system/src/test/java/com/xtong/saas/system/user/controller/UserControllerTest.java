package com.xtong.saas.system.user.controller;

import com.xtong.saas.common.exception.GlobalExceptionHandler;
import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.auth.config.SecurityConfig;
import com.xtong.saas.system.auth.filter.JwtAuthenticationFilter;
import com.xtong.saas.system.auth.handler.RestAccessDeniedHandler;
import com.xtong.saas.system.auth.handler.RestAuthenticationEntryPoint;
import com.xtong.saas.system.auth.session.SessionStore;
import com.xtong.saas.system.auth.token.AccessTokenService;
import com.xtong.saas.system.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证用户管理 HTTP API 的精确权限映射和统一方法授权响应。 */
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

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AccessTokenService accessTokenService;

    @MockitoBean
    private SessionStore sessionStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @ParameterizedTest
    @MethodSource("userPermissionMappings")
    void shouldUseExactPermissionForEveryUserEndpoint(String methodName, String permission) throws Exception {
        Method method = Stream.of(UserController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('" + permission + "')");
    }

    @ParameterizedTest
    @MethodSource("matchingListAuthority")
    @WithMockUser(authorities = "system:user:list")
    void shouldAllowUserListWithMatchingAuthority(long pageNum, long pageSize) throws Exception {
        when(userService.page(any())).thenReturn(new PageResult<>(List.of(), 0, pageNum, pageSize));

        mockMvc.perform(get("/api/v1/system/users")
                        .param("pageNum", String.valueOf(pageNum))
                        .param("pageSize", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @ParameterizedTest
    @MethodSource("mismatchedDeleteAuthority")
    void shouldRejectUserDeleteWithoutDeleteAuthority(String authority) throws Exception {
        mockMvc.perform(delete("/api/v1/system/users/1")
                        .with(user("auditor").authorities(() -> authority)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1102));
    }

    private static Stream<Arguments> userPermissionMappings() {
        return Stream.of(
                Arguments.of("page", "system:user:list"),
                Arguments.of("get", "system:user:detail"),
                Arguments.of("create", "system:user:create"),
                Arguments.of("update", "system:user:update"),
                Arguments.of("enable", "system:user:enable"),
                Arguments.of("disable", "system:user:disable"),
                Arguments.of("resetPassword", "system:user:reset-password"),
                Arguments.of("delete", "system:user:delete"),
                Arguments.of("assignRoles", "system:user:assign-role"));
    }

    private static Stream<Arguments> matchingListAuthority() {
        return Stream.of(Arguments.of(1L, 20L));
    }

    private static Stream<String> mismatchedDeleteAuthority() {
        return Stream.of("system:user:list");
    }

    /** 提供不依赖 Boot 切片自动配置的最小用户 MVC 与 Security 测试上下文。 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            UserController.class,
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
