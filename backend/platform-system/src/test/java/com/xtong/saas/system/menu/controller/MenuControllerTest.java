package com.xtong.saas.system.menu.controller;

import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.menu.dto.CreateMenuDTO;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.dto.UpdateMenuDTO;
import com.xtong.saas.system.menu.enums.MenuType;
import com.xtong.saas.system.menu.service.MenuService;
import com.xtong.saas.system.menu.vo.MenuVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证菜单 Controller 对查询和维护操作使用统一结果包装并正确委派。 */
class MenuControllerTest {

    private final MenuService menuService = mock(MenuService.class);
    private final MenuController controller = new MenuController(menuService);

    @Test
    void shouldWrapCatalogAndManagementQueryResults() {
        List<MenuTreeNodeVO> tree = List.of();
        Set<String> permissions = Set.of("system:user:list");
        when(menuService.getTree()).thenReturn(tree);
        when(menuService.getManagementTree()).thenReturn(tree);
        when(menuService.getPermissionCodes()).thenReturn(permissions);

        assertThat(controller.tree()).isEqualTo(Result.success(tree));
        assertThat(controller.managementTree()).isEqualTo(Result.success(tree));
        assertThat(controller.permissions()).isEqualTo(Result.success(permissions));
    }

    @Test
    void shouldWrapDetailAndCreateResults() {
        MenuVO menu = mock(MenuVO.class);
        CreateMenuDTO command = new CreateMenuDTO(
                null, "业务", MenuType.DIRECTORY, null, null, null, null, 1, true);
        when(menuService.get(10L)).thenReturn(menu);
        when(menuService.create(command)).thenReturn("11");

        assertThat(controller.get(10L)).isEqualTo(Result.success(menu));
        assertThat(controller.create(command)).isEqualTo(Result.success("11"));
    }

    @Test
    void shouldDelegateAllMenuMutations() {
        UpdateMenuDTO command = new UpdateMenuDTO(
                null, "业务", MenuType.DIRECTORY, null, null, null, null, 1, true);

        assertThat(controller.update(10L, command)).isEqualTo(Result.success());
        assertThat(controller.enable(10L)).isEqualTo(Result.success());
        assertThat(controller.disable(10L)).isEqualTo(Result.success());
        assertThat(controller.delete(10L)).isEqualTo(Result.success());
        verify(menuService).update(10L, command);
        verify(menuService).enable(10L);
        verify(menuService).disable(10L);
        verify(menuService).delete(10L);
    }
}
