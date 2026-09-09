package com.xtong.saas.system.menu.service;

import com.xtong.saas.system.menu.service.impl.PermissionServiceImpl;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证权限服务在租户边界内区分管理员全权限和普通角色聚合权限。 */
class PermissionServiceTest {

    private final SystemUserRoleMapper userRoleMapper = mock(SystemUserRoleMapper.class);
    private final SystemRoleMenuMapper roleMenuMapper = mock(SystemRoleMenuMapper.class);
    private final MenuService menuService = mock(MenuService.class);
    private final SystemUserMapper userMapper = mock(SystemUserMapper.class);
    private final PermissionService permissionService = new PermissionServiceImpl(
            userRoleMapper, roleMenuMapper, menuService, userMapper);

    @AfterEach
    void shouldClearTenantContextAfterPermissionLookup() {
        assertThat(TenantContextHolder.currentTenantId()).isEmpty();
    }

    @Test
    void nonPlatformRoleWithoutExplicitAssignmentsShouldHaveNoPermissions() {
        when(userRoleMapper.selectRoleIdsByUserId(1L, 2L)).thenReturn(List.of(11L));
        when(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(1L, List.of(11L))).thenReturn(List.of());

        assertThat(permissionService.loadUserPermissions(1L, 2L)).isEmpty();

        verify(menuService, never()).getPermissionCodes();
        verify(menuService, never()).getPlatformPermissionCodes();
    }

    @Test
    void platformAdminShouldReceiveAllPermissionsWithoutExplicitAssignments() {
        when(userMapper.existsPlatformAdmin(1L, 2L)).thenReturn(true);
        when(menuService.getPermissionCodes()).thenReturn(Set.of("system:user:list"));
        when(menuService.getPlatformPermissionCodes()).thenReturn(Set.of("system:menu:list"));

        assertThat(permissionService.loadUserPermissions(1L, 2L))
                .containsExactly("system:menu:list", "system:platform:admin", "system:user:list");
    }

    @Test
    void ordinaryUserShouldAggregateOnlyEnabledPermissionsFromAssignedRoles() {
        when(userRoleMapper.selectRoleIdsByUserId(1L, 2L)).thenReturn(List.of(11L, 12L));
        when(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(1L, List.of(11L, 12L)))
                .thenReturn(List.of("system:user:list", "system:role:list", "system:user:list"));

        Set<String> permissions = permissionService.loadUserPermissions(1L, 2L);

        assertThat(permissions).containsExactly("system:role:list", "system:user:list");
        verify(roleMenuMapper).selectEnabledPermissionCodesByRoleIds(1L, List.of(11L, 12L));
        verify(menuService, never()).getPermissionCodes();
    }

    @Test
    void shouldNotQueryRoleMenusWhenUserHasNoRoles() {
        when(userRoleMapper.selectRoleIdsByUserId(1L, 2L)).thenReturn(List.of());

        assertThat(permissionService.loadUserPermissions(1L, 2L)).isEmpty();

        verify(roleMenuMapper, never()).selectEnabledPermissionCodesByRoleIds(anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldUseSuppliedTenantOnlyInsideScopedLookup() {
        AtomicLong observedTenantId = new AtomicLong();
        when(userMapper.existsPlatformAdmin(eq(9L), eq(8L))).thenAnswer(invocation -> {
            observedTenantId.set(TenantContextHolder.requireTenantId());
            return false;
        });
        when(userRoleMapper.selectRoleIdsByUserId(9L, 8L)).thenReturn(List.of());

        permissionService.loadUserPermissions(9L, 8L);

        assertThat(observedTenantId).hasValue(9L);
    }
}
