package com.xtong.saas.system.tenant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.tenant.enums.TenantStatus;
import com.xtong.saas.system.tenant.exception.TenantErrorCode;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.tenant.service.TenantService;
import org.springframework.stereotype.Service;

/** 使用全局租户目录查询未删除租户，并强制校验租户启用状态。 */
@Service
public class TenantServiceImpl implements TenantService {

    private final SystemTenantMapper tenantMapper;

    public TenantServiceImpl(SystemTenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public SystemTenant requireEnabledByCode(String tenantCode) {
        SystemTenant tenant = tenantMapper.selectOne(Wrappers.<SystemTenant>query()
                .eq("tenant_code", tenantCode)
                .eq("deleted", false));
        if (tenant == null) {
            throw new BusinessException(TenantErrorCode.TENANT_NOT_FOUND);
        }
        if (tenant.getStatus() != TenantStatus.ENABLED) {
            throw new BusinessException(TenantErrorCode.TENANT_DISABLED);
        }
        return tenant;
    }

    @Override
    public boolean hasAnyTenant() {
        return tenantMapper.selectCount(Wrappers.<SystemTenant>query()
                .eq("deleted", false)) > 0;
    }
}
