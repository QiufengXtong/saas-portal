package com.xtong.saas.system.menu.service.impl;

import com.xtong.saas.system.menu.service.MenuService;
import com.xtong.saas.system.menu.service.PermissionService;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.context.TenantScope;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 在租户作用域内执行角色权限聚合，并为有效租户管理员授予全部按钮权限。 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private final SystemRoleMapper roleMapper;
    private final SystemUserRoleMapper userRoleMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final MenuService menuService;

    public PermissionServiceImpl(
            SystemRoleMapper roleMapper,
            SystemUserRoleMapper userRoleMapper,
            SystemRoleMenuMapper roleMenuMapper,
            MenuService menuService) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuService = menuService;
    }

    @Override
    public Set<String> loadUserPermissions(long tenantId, long userId) {
        return TenantScope.call(tenantId, () -> loadScopedUserPermissions(tenantId, userId));
    }

    private Set<String> loadScopedUserPermissions(long tenantId, long userId) {
        if (roleMapper.existsTenantAdminRole(tenantId, userId)) {
            return stableSet(menuService.getPermissionCodes());
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(tenantId, userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return stableSet(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(tenantId, roleIds));
    }

    private Set<String> stableSet(Iterable<String> permissionCodes) {
        TreeSet<String> sortedCodes = new TreeSet<>();
        for (String permissionCode : permissionCodes) {
            if (permissionCode != null && !permissionCode.isBlank()) {
                sortedCodes.add(permissionCode);
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sortedCodes));
    }
}
