package com.xtong.saas.system.menu.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证只读菜单和权限目录 HTTP API 使用各自精确的权限码。 */
class MenuControllerTest {

    @ParameterizedTest
    @MethodSource("menuPermissionMappings")
    void shouldUseExactPermissionForEveryMenuEndpoint(String methodName, String permission) throws Exception {
        Method method = Stream.of(MenuController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('" + permission + "')");
    }

    private static Stream<Arguments> menuPermissionMappings() {
        return Stream.of(
                Arguments.of("tree", "system:menu:tree"),
                Arguments.of("permissions", "system:permission:list"));
    }
}
