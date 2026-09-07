package com.xtong.saas.system.tenant.service;

import com.xtong.saas.system.tenant.entity.SystemTenant;

/** 提供登录前租户校验和首次初始化所需的全局租户查询。 */
public interface TenantService {

    /** 按租户编码加载并校验启用状态。 */
    SystemTenant requireEnabledByCode(String tenantCode);

    /** 按租户 ID 和编码加锁加载，并校验启用状态。 */
    SystemTenant lockAndRequireEnabled(long tenantId, String tenantCode);

    /** 按租户 ID 加锁加载，并校验启用状态。 */
    SystemTenant lockAndRequireEnabled(long tenantId);

    /** 判断系统中是否已经存在未删除租户。 */
    boolean hasAnyTenant();
}
