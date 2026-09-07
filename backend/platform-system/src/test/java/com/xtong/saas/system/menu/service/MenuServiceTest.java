package com.xtong.saas.system.menu.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.entity.SystemMenu;
import com.xtong.saas.system.menu.enums.MenuStatus;
import com.xtong.saas.system.menu.enums.MenuType;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.menu.service.impl.MenuServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证菜单服务只输出有效节点、稳定构造层级并提供按钮权限码。 */
class MenuServiceTest {

    private final SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
    private final MenuService service = new MenuServiceImpl(menuMapper);

    /** 为直接检查 Lambda Wrapper SQL 的单测初始化菜单实体元数据。 */
    @BeforeAll
    static void initializeMenuTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SystemMenu.class);
    }

    @Test
    void shouldBuildStableTreeBySortOrder() {
        SystemMenu root = menu(100L, null, "system", MenuType.DIRECTORY, 100);
        SystemMenu secondChild = menu(120L, 100L, "role", MenuType.MENU, 20);
        SystemMenu firstChild = menu(110L, 100L, "user", MenuType.MENU, 10);
        when(menuMapper.selectList(any())).thenReturn(List.of(root, secondChild, firstChild));

        List<MenuTreeNodeVO> tree = service.getTree();

        assertThat(tree).extracting(MenuTreeNodeVO::id).containsExactly("100");
        assertThat(tree.getFirst().children()).extracting(MenuTreeNodeVO::id)
                .containsExactly("110", "120");
        Wrapper<SystemMenu> query = capturedQuery();
        assertThat(query.getSqlSegment()).contains("deleted", "status", "sort_order", "id");
        assertThat(parametersOf(query)).containsValues(false, MenuStatus.ENABLED);
    }

    @Test
    void shouldSkipDisabledDeletedAndOrphanNodes() {
        SystemMenu root = menu(100L, null, "system", MenuType.DIRECTORY, 100);
        SystemMenu disabled = menu(110L, 100L, "disabled", MenuType.MENU, 10);
        disabled.setStatus(MenuStatus.DISABLED);
        SystemMenu deleted = menu(120L, 100L, "deleted", MenuType.MENU, 20);
        deleted.setDeleted(true);
        SystemMenu orphan = menu(130L, 999L, "orphan", MenuType.MENU, 30);
        when(menuMapper.selectList(any())).thenReturn(List.of(orphan, deleted, disabled, root));

        List<MenuTreeNodeVO> tree = service.getTree();

        assertThat(tree).extracting(MenuTreeNodeVO::id).containsExactly("100");
        assertThat(tree.getFirst().children()).isEmpty();
    }

    @Test
    void shouldReturnOnlyEnabledButtonPermissionCodesInStableOrder() {
        SystemMenu userList = menu(11001L, 110L, "list", MenuType.BUTTON, 2);
        userList.setPermissionCode("system:user:list");
        SystemMenu roleList = menu(12001L, 120L, "role-list", MenuType.BUTTON, 1);
        roleList.setPermissionCode("system:role:list");
        SystemMenu menuNode = menu(110L, 100L, "user", MenuType.MENU, 1);
        menuNode.setPermissionCode("must-not-return");
        SystemMenu blankCode = menu(13001L, 130L, "blank", MenuType.BUTTON, 3);
        blankCode.setPermissionCode(" ");
        when(menuMapper.selectList(any())).thenReturn(List.of(userList, blankCode, menuNode, roleList));

        Set<String> codes = service.getPermissionCodes();

        assertThat(codes).containsExactly("system:role:list", "system:user:list");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Wrapper<SystemMenu> capturedQuery() {
        ArgumentCaptor<Wrapper<SystemMenu>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(menuMapper).selectList(captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> parametersOf(Wrapper<SystemMenu> query) {
        assertThat(query).isInstanceOf(AbstractWrapper.class);
        return ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs();
    }

    private SystemMenu menu(long id, Long parentId, String name, MenuType type, int sortOrder) {
        SystemMenu menu = new SystemMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setSortOrder(sortOrder);
        menu.setVisible(true);
        menu.setStatus(MenuStatus.ENABLED);
        menu.setDeleted(false);
        return menu;
    }
}
