package com.xtong.saas.system.tenant.context;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.tenant.exception.TenantErrorCode;

import java.util.OptionalLong;

/** 保存当前线程受信任的租户 ID，并提供强制读取和清理能力。 */
public final class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static OptionalLong currentTenantId() {
        Long tenantId = TENANT_ID.get();
        return tenantId == null ? OptionalLong.empty() : OptionalLong.of(tenantId);
    }

    public static long requireTenantId() {
        return currentTenantId()
                .orElseThrow(() -> new BusinessException(TenantErrorCode.TENANT_CONTEXT_MISSING));
    }

    static void set(long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /** 清除当前线程租户，供请求边界在异常与复用线程前执行兜底清理。 */
    public static void clear() {
        TENANT_ID.remove();
    }
}
