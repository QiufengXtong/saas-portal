package com.xtong.saas.system.tenant.service;

import com.xtong.saas.system.tenant.entity.SystemTenant;

/** 提供登录前租户校验和首次初始化所需的全局租户查询。 */
public interface TenantService {

    SystemTenant requireEnabledByCode(String tenantCode);

    SystemTenant lockAndRequireEnabled(long tenantId, String tenantCode);

    SystemTenant lockAndRequireEnabled(long tenantId);

    boolean hasAnyTenant();
}
