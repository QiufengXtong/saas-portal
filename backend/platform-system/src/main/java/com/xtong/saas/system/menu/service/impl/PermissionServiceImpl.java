package com.xtong.saas.system.menu.service.impl;

import com.xtong.saas.system.menu.service.MenuService;
import com.xtong.saas.system.menu.service.PermissionService;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 在租户作用域内执行角色权限聚合，仅为平台管理员自动授予全部权限。 */
@Service
public class PermissionServiceImpl implements PermissionService {
    public static final String PLATFORM_ADMIN_AUTHORITY = "system:platform:admin";

    private final SystemUserRoleMapper userRoleMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final MenuService menuService;
    private final SystemUserMapper userMapper;

    /** 创建权限服务并注入角色、关联与菜单领域依赖。 */
    public PermissionServiceImpl(
            SystemUserRoleMapper userRoleMapper,
            SystemRoleMenuMapper roleMenuMapper,
            MenuService menuService,
            SystemUserMapper userMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuService = menuService;
        this.userMapper = userMapper;
    }

    /** 在指定租户作用域内加载用户当前有效权限。 */
    @Override
    public Set<String> loadUserPermissions(long tenantId, long userId) {
        return TenantScope.call(tenantId, () -> loadScopedUserPermissions(tenantId, userId));
    }

    /** 聚合租户角色权限，并仅为平台管理员追加平台级权限。 */
    private Set<String> loadScopedUserPermissions(long tenantId, long userId) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (userMapper.existsPlatformAdmin(tenantId, userId)) {
            permissions.add(PLATFORM_ADMIN_AUTHORITY);
            permissions.addAll(menuService.getPermissionCodes());
            permissions.addAll(menuService.getPlatformPermissionCodes());
        } else {
            List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(tenantId, userId);
            if (!roleIds.isEmpty()) {
                permissions.addAll(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(tenantId, roleIds));
            }
        }
        return stableSet(permissions);
    }
    /** 去重并按字典序稳定排列权限码，返回不可变集合。 */
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
