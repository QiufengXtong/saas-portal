package com.xtong.saas.system.bootstrap;

import com.xtong.saas.system.bootstrap.config.BootstrapProperties;
import com.xtong.saas.system.bootstrap.mapper.SystemBootstrapLockMapper;
import com.xtong.saas.system.role.entity.SystemRole;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.tenant.service.TenantService;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用真实 H2 事务验证初始化中途失败时不会留下半成品租户。 */
class SystemBootstrapTransactionTest {

    @Test
    void shouldRollbackTenantWhenAdminGraphCreationFails() throws Exception {
        DataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        SystemBootstrapLockMapper bootstrapLockMapper = mock(SystemBootstrapLockMapper.class);
        TenantService tenantService = mock(TenantService.class);
        SystemTenantMapper tenantMapper = mock(SystemTenantMapper.class);
        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        SystemUserMapper userMapper = mock(SystemUserMapper.class);
        SystemUserRoleMapper userRoleMapper = mock(SystemUserRoleMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        doAnswer(ignored -> (long) query(dataSource, "select id from sys_bootstrap_lock where id = 1 for update"))
                .when(bootstrapLockMapper).lockInitialization();
        when(tenantService.hasAnyTenant()).thenAnswer(ignored -> query(dataSource, "select count(*) from sys_tenant") > 0);
        doAnswer(invocation -> {
            SystemTenant tenant = invocation.getArgument(0);
            update(dataSource, "insert into sys_tenant(id, tenant_code, tenant_name) values (101, 'default', 'Default')");
            tenant.setId(101L);
            return 1;
        }).when(tenantMapper).insert(any(SystemTenant.class));
        when(tenantMapper.lockByIdForAdminInvariant(101L)).thenAnswer(
                ignored -> (long) query(dataSource, "select id from sys_tenant where id = 101 for update"));
        doThrow(new IllegalStateException("role insert failed"))
                .when(roleMapper).insert(any(SystemRole.class));
        when(passwordEncoder.encode("Secret123")).thenReturn("bcrypt");
        SystemBootstrapInitializer initializer = new SystemBootstrapInitializer(
                new BootstrapProperties("default", "Default", "admin", "Secret123"),
                bootstrapLockMapper,
                tenantService,
                tenantMapper,
                roleMapper,
                userMapper,
                userRoleMapper,
                passwordEncoder);
        ProxyFactory proxyFactory = new ProxyFactory(initializer);
        proxyFactory.setProxyTargetClass(true);
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transactionInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxyFactory.addAdvice(transactionInterceptor);
        SystemBootstrapInitializer transactionalInitializer =
                (SystemBootstrapInitializer) proxyFactory.getProxy();

        assertThatThrownBy(transactionalInitializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("role insert failed");

        try (Connection connection = dataSource.getConnection()) {
            assertThat(query(connection, "select count(*) from sys_tenant")).isZero();
            assertThat(query(connection, "select count(*) from sys_role")).isZero();
            assertThat(query(connection, "select count(*) from sys_user")).isZero();
        }
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:bootstrap_rollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static int query(DataSource dataSource, String sql) throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return query(connection, sql);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static int query(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static void update(DataSource dataSource, String sql) throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
