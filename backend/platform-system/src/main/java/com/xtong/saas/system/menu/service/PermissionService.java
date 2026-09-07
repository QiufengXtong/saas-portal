package com.xtong.saas.system.menu.service;

import java.util.Set;

/** 在受信租户范围内加载用户的有效 RBAC 权限集合。 */
public interface PermissionService {

    /** 加载指定租户用户当前有效的权限码集合。 */
    Set<String> loadUserPermissions(long tenantId, long userId);
}
