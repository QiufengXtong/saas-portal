package com.xtong.saas.system.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** 将受信任租户上下文自动追加到所有租户表 SQL，并在分页前执行隔离。 */
@Configuration(proxyBeanMethods = false)
public class TenantMyBatisConfig {

    private static final Set<String> GLOBAL_TABLES = Set.of(
            "sys_tenant",
            "sys_menu",
            "sys_bootstrap_lock",
            "flyway_schema_history"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(PaginationInnerInterceptor pagination) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContextHolder.requireTenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return GLOBAL_TABLES.stream().anyMatch(tableName::equalsIgnoreCase);
            }
        };
    }
}
