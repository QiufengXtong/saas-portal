package com.xtong.saas.system.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.xtong.saas.system.tenant.context.TenantScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证租户 SQL 处理器的表边界、上下文取值和拦截器顺序。 */
class TenantLineHandlerTest {

    private final TenantMyBatisConfig config = new TenantMyBatisConfig();
    private final TenantLineHandler handler = config.tenantLineHandler();

    @Test
    void shouldIgnoreOnlyGlobalTables() {
        assertThat(handler.ignoreTable("sys_tenant")).isTrue();
        assertThat(handler.ignoreTable("sys_menu")).isTrue();
        assertThat(handler.ignoreTable("sys_bootstrap_lock")).isTrue();
        assertThat(handler.ignoreTable("flyway_schema_history")).isTrue();
        assertThat(handler.ignoreTable("sys_user")).isFalse();
        assertThat(handler.ignoreTable("sys_role")).isFalse();
        assertThat(handler.ignoreTable("sys_user_role")).isFalse();
        assertThat(handler.ignoreTable("sys_role_menu")).isFalse();
    }

    @Test
    void shouldReadTenantIdOnlyFromTrustedScope() {
        TenantScope.run(42L, () -> assertThat(handler.getTenantId().toString()).isEqualTo("42"));
    }

    @Test
    void shouldApplyTenantIsolationBeforePagination() {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(pagination);

        assertThat(interceptor.getInterceptors())
                .hasSize(2)
                .satisfiesExactly(
                        inner -> assertThat(inner).isInstanceOf(TenantLineInnerInterceptor.class),
                        inner -> assertThat(inner).isSameAs(pagination));
    }
}
