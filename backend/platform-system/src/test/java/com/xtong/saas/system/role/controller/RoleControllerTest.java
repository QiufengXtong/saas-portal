package com.xtong.saas.system.role.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证角色管理 HTTP API 的每个方法使用与操作语义严格对应的权限码。 */
class RoleControllerTest {

    @ParameterizedTest
    @MethodSource("rolePermissionMappings")
    void shouldUseExactPermissionForEveryRoleEndpoint(String methodName, String permission) throws Exception {
        Method method = Stream.of(RoleController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('" + permission + "')");
    }

    private static Stream<Arguments> rolePermissionMappings() {
        return Stream.of(
                Arguments.of("page", "system:role:list"),
                Arguments.of("get", "system:role:detail"),
                Arguments.of("create", "system:role:create"),
                Arguments.of("update", "system:role:update"),
                Arguments.of("enable", "system:role:enable"),
                Arguments.of("disable", "system:role:disable"),
                Arguments.of("delete", "system:role:delete"),
                Arguments.of("assignMenus", "system:role:assign-menu"));
    }
}
