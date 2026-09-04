package com.xtong.saas.system.tenant.context;

import java.util.OptionalLong;
import java.util.function.Supplier;

/** 在可嵌套且必定清理的边界内运行指定租户的受信任操作。 */
public final class TenantScope {

    private TenantScope() {
    }

    public static void run(long tenantId, Runnable action) {
        call(tenantId, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T call(long tenantId, Supplier<T> action) {
        OptionalLong previousTenantId = TenantContextHolder.currentTenantId();
        TenantContextHolder.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previousTenantId.isPresent()) {
                TenantContextHolder.set(previousTenantId.getAsLong());
            } else {
                TenantContextHolder.clear();
            }
        }
    }
}
