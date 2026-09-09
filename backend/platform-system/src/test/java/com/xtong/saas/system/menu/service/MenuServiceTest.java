package com.xtong.saas.system.menu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.mybatis.AuditorProvider;
import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.menu.dto.CreateMenuDTO;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.dto.UpdateMenuDTO;
import com.xtong.saas.system.menu.entity.SystemMenu;
import com.xtong.saas.system.menu.enums.MenuStatus;
import com.xtong.saas.system.menu.enums.MenuType;
import com.xtong.saas.system.menu.enums.PermissionScope;
import com.xtong.saas.system.menu.exception.MenuErrorCode;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.menu.model.MenuAffectedUser;
import com.xtong.saas.system.menu.service.impl.MenuServiceImpl;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证菜单树、字段约束、内置保护、层级安全和权限会话失效。 */
class MenuServiceTest {

    private final SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
    private final SystemRoleMenuMapper roleMenuMapper = mock(SystemRoleMenuMapper.class);
    private final SystemUserMapper userMapper = mock(SystemUserMapper.class);
    private final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);
    private final AuditorProvider auditorProvider = mock(AuditorProvider.class);
    private final MenuService service = new MenuServiceImpl(
            menuMapper, roleMenuMapper, userMapper, sessionRevocationService, auditorProvider);

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
        assertThat(tree.getFirst().children()).extracting(MenuTreeNodeVO::id).containsExactly("110", "120");
        Wrapper<SystemMenu> query = capturedQuery();
        assertThat(query.getSqlSegment()).contains("deleted", "status", "sort_order", "id");
        assertThat(parametersOf(query)).containsValues(false, MenuStatus.ENABLED);
    }

    @Test
    void shouldIncludeDisabledNodesOnlyInManagementTree() {
        SystemMenu root = menu(100L, null, "system", MenuType.DIRECTORY, 100);
        SystemMenu disabled = menu(110L, 100L, "disabled", MenuType.MENU, 10);
        disabled.setStatus(MenuStatus.DISABLED);
        when(menuMapper.selectList(any())).thenReturn(List.of(root), List.of(root, disabled));

        assertThat(service.getTree().getFirst().children()).isEmpty();
        assertThat(service.getManagementTree().getFirst().children())
                .extracting(MenuTreeNodeVO::status).containsExactly(MenuStatus.DISABLED);
    }

    @Test
    void shouldReturnOnlyEnabledButtonPermissionCodesInStableOrder() {
        SystemMenu userList = menu(11001L, 110L, "list", MenuType.BUTTON, 2);
        userList.setPermissionCode("system:user:list");
        userList.setVisible(false);
        SystemMenu roleList = menu(12001L, 120L, "role-list", MenuType.BUTTON, 1);
        roleList.setPermissionCode("system:role:list");
        roleList.setVisible(false);
        SystemMenu platformPermission = menu(13001L, 130L, "menu-list", MenuType.BUTTON, 3);
        platformPermission.setPermissionCode("system:menu:list");
        platformPermission.setPermissionScope(PermissionScope.PLATFORM);
        SystemMenu menuNode = menu(110L, 100L, "user", MenuType.MENU, 1);
        menuNode.setPermissionCode("must-not-return");
        when(menuMapper.selectList(any())).thenReturn(List.of(userList, menuNode, roleList, platformPermission));

        Set<String> codes = service.getPermissionCodes();

        assertThat(codes).containsExactly("system:role:list", "system:user:list");
        assertThat(service.getPlatformPermissionCodes()).containsExactly("system:menu:list");
    }

    @Test
    void shouldCreateButtonAndInvalidatePlatformAdministrators() {
        SystemMenu parent = menu(110L, 100L, "user", MenuType.MENU, 10);
        when(menuMapper.selectOne(any())).thenReturn(parent);
        when(roleMenuMapper.selectPlatformAdminUsers()).thenReturn(List.of(new MenuAffectedUser(1L, 9L)));
        doAnswer(invocation -> {
            ((SystemMenu) invocation.getArgument(0)).setId(900L);
            return 1;
        }).when(menuMapper).insert(any(SystemMenu.class));

        String id = service.create(new CreateMenuDTO(
                110L, "导出用户", MenuType.BUTTON, null, null, null,
                "system:user:export", 20, false));

        assertThat(id).isEqualTo("900");
        ArgumentCaptor<SystemMenu> createdMenu = ArgumentCaptor.forClass(SystemMenu.class);
        verify(menuMapper).insert(createdMenu.capture());
        assertThat(createdMenu.getValue().getPermissionScope()).isEqualTo(PermissionScope.TENANT);
        verify(userMapper).incrementAuthVersions(1L, List.of(9L));
        verify(sessionRevocationService).revokeAllUserSessions(1L, 9L);
    }

    @Test
    void shouldRejectInvalidButtonFields() {
        assertThatThrownBy(() -> service.create(new CreateMenuDTO(
                110L, "导出用户", MenuType.BUTTON, "/invalid", null, null,
                "system:user:export", 20, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MenuErrorCode.INVALID_MENU_FIELDS);
        verifyNoInteractions(roleMenuMapper, userMapper, sessionRevocationService);
    }

    @Test
    void shouldRejectCyclicParent() {
        SystemMenu directory = menu(10L, null, "root", MenuType.DIRECTORY, 1);
        when(menuMapper.selectOne(any())).thenReturn(directory);

        assertThatThrownBy(() -> service.update(10L, new UpdateMenuDTO(
                10L, "root", MenuType.DIRECTORY, null, null, null, null, 1, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MenuErrorCode.MENU_CYCLE);
        verify(menuMapper, never()).updateById(any(SystemMenu.class));
    }

    @Test
    void shouldRejectMovingMenuAcrossPermissionScopes() {
        SystemMenu tenantMenu = menu(110L, 100L, "tenant", MenuType.MENU, 1);
        tenantMenu.setRoutePath("/tenant");
        tenantMenu.setComponent("TenantView");
        SystemMenu platformParent = menu(130L, null, "platform", MenuType.DIRECTORY, 1);
        platformParent.setPermissionScope(PermissionScope.PLATFORM);
        when(menuMapper.selectOne(any())).thenReturn(tenantMenu, platformParent);

        assertThatThrownBy(() -> service.update(110L, new UpdateMenuDTO(
                130L, "tenant", MenuType.MENU, "/tenant", "TenantView", null, null, 1, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MenuErrorCode.INVALID_PARENT);

        verify(menuMapper, never()).updateById(any(SystemMenu.class));
        verifyNoInteractions(roleMenuMapper, userMapper, sessionRevocationService);
    }

    @Test
    void shouldProtectBuiltInMenuFromCoreFieldChangesAndDisable() {
        SystemMenu builtIn = menu(100L, null, "system", MenuType.DIRECTORY, 1);
        builtIn.setBuiltIn(true);
        when(menuMapper.selectOne(any())).thenReturn(builtIn);

        assertThatThrownBy(() -> service.update(100L, new UpdateMenuDTO(
                null, "system", MenuType.DIRECTORY, "/changed", null, null, null, 1, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MenuErrorCode.BUILT_IN_MENU_PROTECTED);
        assertThatThrownBy(() -> service.disable(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MenuErrorCode.BUILT_IN_MENU_PROTECTED);
    }

    @Test
    void shouldInvalidateAffectedUsersWhenPermissionCodeChanges() {
        SystemMenu button = menu(20L, 11L, "export", MenuType.BUTTON, 1);
        button.setPermissionCode("system:user:export");
        button.setVisible(false);
        SystemMenu parent = menu(11L, 1L, "user", MenuType.MENU, 1);
        SystemMenu root = menu(1L, null, "system", MenuType.DIRECTORY, 1);
        when(menuMapper.selectOne(any())).thenReturn(button, parent, root);
        when(menuMapper.selectList(any())).thenReturn(List.of());
        when(roleMenuMapper.selectUsersAffectedByMenu(20L))
                .thenReturn(List.of(new MenuAffectedUser(1L, 9L), new MenuAffectedUser(1L, 9L)));

        service.update(20L, new UpdateMenuDTO(
                11L, "export", MenuType.BUTTON, null, null, null,
                "system:user:download", 1, false));

        verify(userMapper).incrementAuthVersions(1L, List.of(9L));
        verify(sessionRevocationService).revokeAllUserSessions(1L, 9L);
    }

    @Test
    void shouldRejectDeletionWhenMenuHasChildrenOrRoleAssignments() {
        SystemMenu menu = menu(20L, null, "custom", MenuType.DIRECTORY, 1);
        when(menuMapper.selectOne(any())).thenReturn(menu);
        when(menuMapper.selectCount(any())).thenReturn(1L, 0L);

        assertThatThrownBy(() -> service.delete(20L))
                .extracting("errorCode").isEqualTo(MenuErrorCode.MENU_HAS_CHILDREN);
        when(roleMenuMapper.countByMenuId(20L)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(20L))
                .extracting("errorCode").isEqualTo(MenuErrorCode.MENU_ASSIGNED_TO_ROLE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Wrapper<SystemMenu> capturedQuery() {
        ArgumentCaptor<Wrapper<SystemMenu>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(menuMapper).selectList(captor.capture());
        return captor.getValue();
    }

    /** 获取 Lambda Wrapper 的绑定参数以验证查询语义而不依赖参数名称。 */
    private Map<String, Object> parametersOf(Wrapper<SystemMenu> query) {
        assertThat(query).isInstanceOf(AbstractWrapper.class);
        return ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs();
    }

    /** 构造具备默认有效状态的菜单测试实体。 */
    private SystemMenu menu(long id, Long parentId, String name, MenuType type, int sortOrder) {
        SystemMenu menu = new SystemMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setSortOrder(sortOrder);
        menu.setVisible(true);
        menu.setStatus(MenuStatus.ENABLED);
        menu.setBuiltIn(false);
        menu.setDeleted(false);
        return menu;
    }
}
