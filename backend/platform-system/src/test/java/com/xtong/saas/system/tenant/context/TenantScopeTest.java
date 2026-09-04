package com.xtong.saas.system.tenant.context;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.tenant.exception.TenantErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证租户作用域的嵌套恢复、异常清理和缺失上下文保护。 */
class TenantScopeTest {

    @AfterEach
    void assertContextWasCleaned() {
        assertThat(TenantContextHolder.currentTenantId()).isEmpty();
    }

    @Test
    void shouldRestoreOuterTenantAfterNestedScope() {
        TenantScope.run(10L, () -> {
            assertThat(TenantContextHolder.requireTenantId()).isEqualTo(10L);
            TenantScope.run(20L,
                    () -> assertThat(TenantContextHolder.requireTenantId()).isEqualTo(20L));
            assertThat(TenantContextHolder.requireTenantId()).isEqualTo(10L);
        });

        assertThat(TenantContextHolder.currentTenantId()).isEmpty();
    }

    @Test
    void shouldClearTenantAfterScopedActionFails() {
        assertThatThrownBy(() -> TenantScope.run(10L, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContextHolder.currentTenantId()).isEmpty();
    }

    @Test
    void shouldReturnValueFromScopedCall() {
        String value = TenantScope.call(10L,
                () -> "tenant-" + TenantContextHolder.requireTenantId());

        assertThat(value).isEqualTo("tenant-10");
    }

    @Test
    void shouldRejectMissingTenantInsteadOfGuessingDefault() {
        assertThatThrownBy(TenantContextHolder::requireTenantId)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TenantErrorCode.TENANT_CONTEXT_MISSING);
    }
}
