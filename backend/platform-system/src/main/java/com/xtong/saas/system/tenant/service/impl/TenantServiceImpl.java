package com.xtong.saas.system.tenant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.identity.IdentityNormalizer;
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
        String normalizedCode = IdentityNormalizer.requireManagement(tenantCode);
        SystemTenant tenant = tenantMapper.selectOne(Wrappers.<SystemTenant>query().lambda()
                .eq(SystemTenant::getTenantCode, normalizedCode)
                .eq(SystemTenant::getDeleted, false));
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
        return tenantMapper.selectCount(Wrappers.<SystemTenant>query().lambda()
                .eq(SystemTenant::getDeleted, false)) > 0;
    }

    @Override
    public SystemTenant lockAndRequireEnabled(long tenantId, String tenantCode) {
        String normalizedCode = IdentityNormalizer.requireManagement(tenantCode);
        SystemTenant tenant = tenantMapper.lockByIdAndCodeForAuthentication(tenantId, normalizedCode);
        if (tenant == null) {
            throw new BusinessException(TenantErrorCode.TENANT_NOT_FOUND);
        }
        if (tenant.getStatus() != TenantStatus.ENABLED) {
            throw new BusinessException(TenantErrorCode.TENANT_DISABLED);
        }
        return tenant;
    }

    @Override
    public SystemTenant lockAndRequireEnabled(long tenantId) {
        SystemTenant tenant = tenantMapper.lockByIdForAuthentication(tenantId);
        if (tenant == null) {
            throw new BusinessException(TenantErrorCode.TENANT_NOT_FOUND);
        }
        if (tenant.getStatus() != TenantStatus.ENABLED) {
            throw new BusinessException(TenantErrorCode.TENANT_DISABLED);
        }
        return tenant;
    }
}
