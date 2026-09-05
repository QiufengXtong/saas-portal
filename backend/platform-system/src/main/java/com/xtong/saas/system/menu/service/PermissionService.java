package com.xtong.saas.system.menu.service;

import java.util.Set;

/** 在受信租户范围内加载用户的有效 RBAC 权限集合。 */
public interface PermissionService {

    Set<String> loadUserPermissions(long tenantId, long userId);
}
