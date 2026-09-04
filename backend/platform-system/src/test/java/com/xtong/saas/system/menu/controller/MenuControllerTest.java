package com.xtong.saas.system.menu.controller;

import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.service.MenuService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证只读菜单 Controller 对菜单树和权限目录使用统一结果包装。 */
class MenuControllerTest {

    private final MenuService menuService = mock(MenuService.class);
    private final MenuController controller = new MenuController(menuService);

    @Test
    void shouldWrapReadOnlyCatalogResults() {
        List<MenuTreeNodeVO> tree = List.of();
        Set<String> permissions = Set.of("system:user:list");
        when(menuService.getTree()).thenReturn(tree);
        when(menuService.getPermissionCodes()).thenReturn(permissions);

        assertThat(controller.tree()).isEqualTo(Result.success(tree));
        assertThat(controller.permissions()).isEqualTo(Result.success(permissions));
    }
}
