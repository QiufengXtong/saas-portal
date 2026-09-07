package com.xtong.saas.system.migration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.xtong.saas.common.mybatis.MyBatisCommonConfig;
import com.xtong.saas.system.bootstrap.mapper.SystemBootstrapLockMapper;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.menu.model.MenuAffectedUser;
import com.xtong.saas.system.role.entity.SystemRoleMenu;
import com.xtong.saas.system.role.entity.SystemUserRole;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.config.TenantMyBatisConfig;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 在 H2 MySQL 模式中验证 Mapper XML 的加载、参数绑定和租户边界。 */
class MapperXmlIntegrationTest {

    private AnnotationConfigApplicationContext context;
    private org.apache.ibatis.session.Configuration configuration;
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private SystemBootstrapLockMapper bootstrapLockMapper;
    private SystemTenantMapper tenantMapper;
    private SystemUserMapper userMapper;
    private SystemRoleMapper roleMapper;
    private SystemMenuMapper menuMapper;
    private SystemUserRoleMapper userRoleMapper;
    private SystemRoleMenuMapper roleMenuMapper;

    /** 每个用例启动独立数据库与 Mapper 上下文，避免数据和会话缓存相互影响。 */
    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        context = new AnnotationConfigApplicationContext(MapperTestConfiguration.class);
        configuration = context.getBean(SqlSessionFactory.class).getConfiguration();
        DataSource dataSource = context.getBean(DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        bootstrapLockMapper = context.getBean(SystemBootstrapLockMapper.class);
        tenantMapper = context.getBean(SystemTenantMapper.class);
        userMapper = context.getBean(SystemUserMapper.class);
        roleMapper = context.getBean(SystemRoleMapper.class);
        menuMapper = context.getBean(SystemMenuMapper.class);
        userRoleMapper = context.getBean(SystemUserRoleMapper.class);
        roleMenuMapper = context.getBean(SystemRoleMenuMapper.class);
    }

    /** 释放独立内存库和 Spring 上下文，并清理线程租户状态。 */
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (context != null) {
            try {
                if (jdbcTemplate != null) {
                    jdbcTemplate.execute("SHUTDOWN");
                }
            } finally {
                context.close();
            }
        }
    }

    @Test
    void shouldLoadLockMapperStatements() {
        assertXmlStatement(SystemBootstrapLockMapper.class, "lockInitialization",
                "mapper/bootstrap/SystemBootstrapLockMapper.xml");
        assertXmlStatement(SystemTenantMapper.class, "lockByIdForAdminInvariant",
                "mapper/tenant/SystemTenantMapper.xml");
        assertXmlStatement(SystemTenantMapper.class, "lockByIdAndCodeForAuthentication",
                "mapper/tenant/SystemTenantMapper.xml");
        assertXmlStatement(SystemTenantMapper.class, "lockByIdForAuthentication",
                "mapper/tenant/SystemTenantMapper.xml");
    }

    @Test
    void shouldLockBootstrapAndTenantRows() {
        insertTenant(1L, "acme");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(TenantContextHolder.currentTenantId()).isEmpty();
            assertThat(bootstrapLockMapper.lockInitialization()).isEqualTo(1L);
            assertThat(tenantMapper.lockByIdAndCodeForAuthentication(1L, "acme").getId())
                    .isEqualTo(1L);
            assertThat(tenantMapper.lockByIdForAuthentication(1L).getTenantCode()).isEqualTo("acme");
            assertThat(tenantMapper.lockByIdForAdminInvariant(1L)).isEqualTo(1L);
        });
    }

    @Test
    void shouldExcludeMissingMismatchedAndDeletedTenantsFromLockQueries() {
        insertTenant(1L, "acme");
        insertTenant(2L, "deleted");
        jdbcTemplate.update("UPDATE sys_tenant SET deleted = 1 WHERE id = ?", 2L);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(tenantMapper.lockByIdAndCodeForAuthentication(1L, "other")).isNull();
            assertThat(tenantMapper.lockByIdAndCodeForAuthentication(2L, "deleted")).isNull();
            assertThat(tenantMapper.lockByIdForAuthentication(2L)).isNull();
            assertThat(tenantMapper.lockByIdForAdminInvariant(2L)).isNull();
            assertThat(tenantMapper.lockByIdAndCodeForAuthentication(99L, "missing")).isNull();
            assertThat(tenantMapper.lockByIdForAuthentication(99L)).isNull();
            assertThat(tenantMapper.lockByIdForAdminInvariant(99L)).isNull();
        });
    }

    @Test
    void shouldLoadUserAndRoleStatementsFromXml() {
        for (String method : List.of("countByTenantAndUsernameIncludingDeleted", "incrementAuthVersion",
                "incrementAuthVersions", "logicalDeleteWithAudit")) {
            assertXmlStatement(SystemUserMapper.class, method, "mapper/user/SystemUserMapper.xml");
        }
        for (String method : List.of("countByTenantAndCodeIncludingDeleted", "existsTenantAdminRole",
                "countByTenantAndIds", "containsTenantAdminRole", "countEnabledTenantAdminUsers",
                "logicalDeleteWithAudit")) {
            assertXmlStatement(SystemRoleMapper.class, method, "mapper/role/SystemRoleMapper.xml");
        }
    }

    @Test
    void shouldReserveDeletedUsernamesAndRoleCodesWithinTheirTenant() {
        insertTenant(1L, "acme");
        insertTenant(2L, "other");
        insertUser(11L, 1L, "reserved", "ENABLED", 1, 7);
        insertUser(21L, 2L, "reserved", "ENABLED", 0, 8);
        insertUser(22L, 2L, "foreign-only", "ENABLED", 1, 9);
        insertRole(101L, 1L, "RESERVED", "ENABLED", 0, 1);
        insertRole(201L, 2L, "RESERVED", "ENABLED", 0, 0);
        insertRole(202L, 2L, "FOREIGN_ONLY", "ENABLED", 0, 1);

        TenantScope.run(1L, () -> {
            assertThat(userMapper.countByTenantAndUsernameIncludingDeleted(1L, "reserved")).isEqualTo(1);
            assertThat(roleMapper.countByTenantAndCodeIncludingDeleted(1L, "RESERVED")).isEqualTo(1);
            assertThat(userMapper.countByTenantAndUsernameIncludingDeleted(1L, "foreign-only")).isZero();
            assertThat(roleMapper.countByTenantAndCodeIncludingDeleted(1L, "FOREIGN_ONLY")).isZero();
            assertThat(userMapper.countByTenantAndUsernameIncludingDeleted(2L, "reserved")).isZero();
            assertThat(roleMapper.countByTenantAndCodeIncludingDeleted(2L, "RESERVED")).isZero();
        });
        TenantScope.run(2L, () -> {
            assertThat(userMapper.countByTenantAndUsernameIncludingDeleted(2L, "reserved")).isEqualTo(1);
            assertThat(roleMapper.countByTenantAndCodeIncludingDeleted(2L, "RESERVED")).isEqualTo(1);
        });
    }

    @Test
    void shouldIncrementOnlyRequestedLiveUsersInCurrentTenant() {
        insertUser(11L, 1L, "first", "ENABLED", 0, 7);
        insertUser(12L, 1L, "disabled", "DISABLED", 0, 20);
        insertUser(13L, 1L, "deleted", "ENABLED", 1, 30);
        insertUser(14L, 1L, "untouched", "ENABLED", 0, 40);
        insertUser(21L, 2L, "foreign", "ENABLED", 0, 50);

        TenantScope.run(1L, () -> {
            assertThat(userMapper.incrementAuthVersion(1L, 11L)).isEqualTo(1);
            assertThat(userMapper.incrementAuthVersion(1L, 13L)).isZero();
            assertThat(userMapper.incrementAuthVersion(1L, 21L)).isZero();
            assertThat(userMapper.incrementAuthVersion(2L, 21L)).isZero();
            assertThat(userMapper.incrementAuthVersions(1L, List.of(11L, 12L, 13L, 21L, 99L)))
                    .isEqualTo(2);
            assertThat(userMapper.incrementAuthVersions(2L, List.of(21L))).isZero();
        });
        assertThat(jdbcTemplate.queryForList("SELECT auth_version FROM sys_user ORDER BY id", Long.class))
                .containsExactly(9L, 21L, 30L, 40L, 50L);
    }

    @Test
    void shouldAccumulateConcurrentSingleAndBatchAuthVersionIncrements() throws Exception {
        insertUser(11L, 1L, "concurrent", "ENABLED", 0, 7);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var single = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                TenantScope.run(1L, () -> {
                    for (int i = 0; i < 10; i++) {
                        assertThat(userMapper.incrementAuthVersion(1L, 11L)).isEqualTo(1);
                    }
                });
                return null;
            });
            var batch = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                TenantScope.run(1L, () -> {
                    for (int i = 0; i < 10; i++) {
                        assertThat(userMapper.incrementAuthVersions(1L, List.of(11L))).isEqualTo(1);
                    }
                });
                return null;
            });
            start.countDown();
            single.get(20, TimeUnit.SECONDS);
            batch.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT auth_version FROM sys_user WHERE id = 11", Long.class))
                .isEqualTo(27L);
    }

    @Test
    void shouldDeleteUserWithAuditAndInvalidateSessionsOnlyOnce() {
        insertUser(11L, 1L, "target", "ENABLED", 0, 7);
        insertUser(12L, 1L, "deleted", "ENABLED", 1, 20);
        insertUser(21L, 2L, "foreign", "ENABLED", 0, 30);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 7, 12, 34, 56, 123_000_000);
        Map<String, Object> deletedBefore = userRow(12L);
        Map<String, Object> foreignBefore = userRow(21L);

        TenantScope.run(1L, () -> {
            assertThat(userMapper.logicalDeleteWithAudit(1L, 11L, 501L, updatedAt)).isEqualTo(1);
            assertThat(userMapper.logicalDeleteWithAudit(1L, 11L, 999L, updatedAt.plusDays(1))).isZero();
            assertThat(userMapper.logicalDeleteWithAudit(1L, 12L, 501L, updatedAt)).isZero();
            assertThat(userMapper.logicalDeleteWithAudit(1L, 21L, 501L, updatedAt)).isZero();
            assertThat(userMapper.logicalDeleteWithAudit(2L, 21L, 501L, updatedAt)).isZero();
            assertThat(userMapper.logicalDeleteWithAudit(1L, 99L, 501L, updatedAt)).isZero();
        });
        assertThat(userRow(11L)).containsEntry("deleted", 1).containsEntry("auth_version", 8L)
                .containsEntry("updated_by", 501L)
                .containsEntry("updated_at", java.sql.Timestamp.valueOf(updatedAt));
        assertThat(userRow(12L)).isEqualTo(deletedBefore);
        assertThat(userRow(21L)).isEqualTo(foreignBefore);
    }

    @Test
    void shouldDeleteRoleWithAuditOnlyWithinCurrentTenantAndOnlyOnce() {
        insertRole(101L, 1L, "TARGET", "ENABLED", 0, 0);
        insertRole(102L, 1L, "DELETED", "ENABLED", 0, 1);
        insertRole(201L, 2L, "FOREIGN", "ENABLED", 0, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 7, 12, 34, 56, 123_000_000);
        Map<String, Object> deletedBefore = roleRow(102L);
        Map<String, Object> foreignBefore = roleRow(201L);

        TenantScope.run(1L, () -> {
            assertThat(roleMapper.logicalDeleteWithAudit(1L, 101L, 502L, updatedAt)).isEqualTo(1);
            assertThat(roleMapper.logicalDeleteWithAudit(1L, 101L, 999L, updatedAt.plusDays(1))).isZero();
            assertThat(roleMapper.logicalDeleteWithAudit(1L, 102L, 502L, updatedAt)).isZero();
            assertThat(roleMapper.logicalDeleteWithAudit(1L, 201L, 502L, updatedAt)).isZero();
            assertThat(roleMapper.logicalDeleteWithAudit(2L, 201L, 502L, updatedAt)).isZero();
            assertThat(roleMapper.logicalDeleteWithAudit(1L, 999L, 502L, updatedAt)).isZero();
        });
        assertThat(roleRow(101L)).containsEntry("deleted", 1).containsEntry("updated_by", 502L)
                .containsEntry("updated_at", java.sql.Timestamp.valueOf(updatedAt));
        assertThat(roleRow(102L)).isEqualTo(deletedBefore);
        assertThat(roleRow(201L)).isEqualTo(foreignBefore);
    }

    @ParameterizedTest
    @CsvSource({"TENANT_ADMIN, ENABLED, 1, 0, true, 1", "TENANT_ADMIN, DISABLED, 1, 0, false, 0",
            "TENANT_ADMIN, ENABLED, 0, 0, false, 1", "TENANT_ADMIN, ENABLED, 1, 1, false, 0",
            "ORDINARY, ENABLED, 1, 0, false, 1"})
    void shouldApplyRoleEligibilityRules(String code, String status, int builtIn, int deleted,
            boolean administrator, long enabledCount) {
        insertUser(11L, 1L, "member", "ENABLED", 0, 0);
        insertRole(101L, 1L, code, status, builtIn, deleted);
        insertRole(201L, 2L, "TENANT_ADMIN", "ENABLED", 1, 0);
        insertUserRole(1001L, 1L, 11L, 101L);
        TenantScope.run(1L, () -> {
            assertThat(roleMapper.existsTenantAdminRole(1L, 11L)).isEqualTo(administrator);
            assertThat(roleMapper.containsTenantAdminRole(1L, Set.of(101L, 201L, 999L)))
                    .isEqualTo(administrator);
            assertThat(roleMapper.countByTenantAndIds(1L, Set.of(101L, 201L, 999L)))
                    .isEqualTo(enabledCount);
            assertThat(roleMapper.countEnabledTenantAdminUsers(1L)).isEqualTo(administrator ? 1 : 0);
            assertThat(roleMapper.existsTenantAdminRole(1L, 99L)).isFalse();
            assertThat(roleMapper.containsTenantAdminRole(1L, Set.of(201L, 999L))).isFalse();
            assertThat(roleMapper.countByTenantAndIds(1L, Set.of(201L, 999L))).isZero();
        });
    }

    @Test
    void shouldCountOnlyLiveEnabledAdminUsersAndRejectCrossTenantRelationships() {
        insertUser(11L, 1L, "active", "ENABLED", 0, 0);
        insertUser(12L, 1L, "disabled", "DISABLED", 0, 0);
        insertUser(13L, 1L, "deleted", "ENABLED", 1, 0);
        insertUser(14L, 1L, "cross-role", "ENABLED", 0, 0);
        insertUser(15L, 1L, "cross-relation", "ENABLED", 0, 0);
        insertUser(21L, 2L, "foreign", "ENABLED", 0, 0);
        insertRole(101L, 1L, "TENANT_ADMIN", "ENABLED", 1, 0);
        insertRole(201L, 2L, "TENANT_ADMIN", "ENABLED", 1, 0);
        insertUserRole(1001L, 1L, 11L, 101L);
        insertUserRole(1002L, 1L, 12L, 101L);
        insertUserRole(1003L, 1L, 13L, 101L);
        insertUserRole(1004L, 1L, 14L, 201L);
        insertUserRole(1005L, 2L, 15L, 201L);
        insertUserRole(1006L, 1L, 21L, 101L);
        insertUserRole(1007L, 2L, 21L, 201L);
        TenantScope.run(1L, () -> {
            assertThat(roleMapper.countEnabledTenantAdminUsers(1L)).isEqualTo(1);
            assertThat(roleMapper.existsTenantAdminRole(1L, 14L)).isFalse();
            assertThat(roleMapper.existsTenantAdminRole(1L, 15L)).isFalse();
            // 角色资格查询本身不读取用户状态，启用用户数量查询负责过滤用户。
            assertThat(roleMapper.existsTenantAdminRole(1L, 12L)).isTrue();
            assertThat(roleMapper.existsTenantAdminRole(1L, 13L)).isTrue();
            assertThat(roleMapper.countEnabledTenantAdminUsers(2L)).isZero();
            assertThat(roleMapper.existsTenantAdminRole(2L, 21L)).isFalse();
            assertThat(roleMapper.containsTenantAdminRole(2L, Set.of(201L))).isFalse();
            assertThat(roleMapper.countByTenantAndIds(2L, Set.of(201L))).isZero();
        });
        TenantScope.run(2L, () -> {
            assertThat(roleMapper.countEnabledTenantAdminUsers(2L)).isEqualTo(1);
            assertThat(roleMapper.existsTenantAdminRole(2L, 21L)).isTrue();
            assertThat(roleMapper.containsTenantAdminRole(2L, Set.of(201L))).isTrue();
            assertThat(roleMapper.countByTenantAndIds(2L, Set.of(101L, 201L))).isEqualTo(1);
        });
    }

    @Test
    void shouldPersistRealCreationAuditForBothRelationshipBatchMappers() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 10, 20, 30, 456_000_000);
        List<SystemUserRole> userRelations = List.of(
                userRole(1L, 11L, 102L, 503L, createdAt), userRole(1L, 11L, 101L, 503L, createdAt));
        List<SystemRoleMenu> menuRelations = List.of(
                roleMenu(1L, 101L, 200L, 504L, createdAt), roleMenu(1L, 101L, 100L, 504L, createdAt));
        TenantScope.run(1L, () -> {
            assertThat(userRoleMapper.insertBatch(userRelations)).isEqualTo(2);
            assertThat(roleMenuMapper.insertBatch(menuRelations)).isEqualTo(2);
        });
        var userRoles = jdbcTemplate.queryForList("SELECT * FROM sys_user_role ORDER BY role_id");
        assertThat(userRoles).hasSize(2).allSatisfy(row -> assertThat(row)
                .containsEntry("tenant_id", 1L).containsEntry("user_id", 11L)
                .containsEntry("created_by", 503L)
                .containsEntry("created_at", java.sql.Timestamp.valueOf(createdAt)));
        assertThat(userRoles).extracting(row -> row.get("role_id")).containsExactly(101L, 102L);
        assertThat(userRoles).extracting(row -> row.get("id"))
                .doesNotContainNull().doesNotHaveDuplicates()
                .containsExactly(userRelations.get(1).getId(), userRelations.get(0).getId());
        var roleMenus = jdbcTemplate.queryForList("SELECT * FROM sys_role_menu ORDER BY menu_id");
        assertThat(roleMenus).hasSize(2).allSatisfy(row -> assertThat(row)
                .containsEntry("tenant_id", 1L).containsEntry("role_id", 101L)
                .containsEntry("created_by", 504L)
                .containsEntry("created_at", java.sql.Timestamp.valueOf(createdAt)));
        assertThat(roleMenus).extracting(row -> row.get("menu_id")).containsExactly(100L, 200L);
        assertThat(roleMenus).extracting(row -> row.get("id"))
                .doesNotContainNull().doesNotHaveDuplicates()
                .containsExactly(menuRelations.get(1).getId(), menuRelations.get(0).getId());
    }

    @Test
    void shouldLoadMenuAndRelationshipStatementsFromXml() {
        for (String method : List.of("countEnabledByIds", "countByPermissionCodeIncludingDeleted",
                "logicalDeleteWithAudit")) {
            assertXmlStatement(SystemMenuMapper.class, method, "mapper/menu/SystemMenuMapper.xml");
        }
        for (String method : List.of("selectRoleIdsByUserId", "deleteByUser", "countByRole",
                "selectUserIdsByRole", "insertBatch")) {
            assertXmlStatement(SystemUserRoleMapper.class, method, "mapper/role/SystemUserRoleMapper.xml");
        }
        for (String method : List.of("deleteByRole", "insertBatch", "selectMenuIdsByRole",
                "selectEnabledPermissionCodesByRoleIds", "countByMenuId",
                "selectUsersAffectedByMenu", "selectTenantAdminUsers")) {
            assertXmlStatement(SystemRoleMenuMapper.class, method, "mapper/role/SystemRoleMenuMapper.xml");
        }
    }

    @Test
    void shouldCountOnlyRequestedLiveEnabledGlobalMenus() {
        insertMenu(901L, "BUTTON", "test:active", "ENABLED", 0);
        insertMenu(902L, "BUTTON", "test:disabled", "DISABLED", 0);
        insertMenu(903L, "BUTTON", "test:deleted", "ENABLED", 1);
        insertMenu(904L, "MENU", null, "ENABLED", 0);
        insertMenu(905L, "BUTTON", "test:unrequested", "ENABLED", 0);

        assertThat(menuMapper.countEnabledByIds(Set.of(901L, 902L, 903L, 904L, 999L))).isEqualTo(2);
        assertThat(menuMapper.countEnabledByIds(Set.of(902L, 903L, 999L))).isZero();
        TenantScope.run(2L, () -> assertThat(menuMapper.countEnabledByIds(Set.of(901L, 904L))).isEqualTo(2));
    }

    @Test
    void shouldLocateMenuAssignmentsAndAffectedUsersAcrossTenants() {
        insertUser(11L, 1L, "member", "ENABLED", 0, 0);
        insertUser(12L, 1L, "admin", "ENABLED", 0, 0);
        insertUser(21L, 2L, "foreign-admin", "ENABLED", 0, 0);
        insertUser(22L, 2L, "deleted-member", "ENABLED", 1, 0);
        insertRole(101L, 1L, "MEMBER", "ENABLED", 0, 0);
        insertRole(102L, 1L, "TENANT_ADMIN", "ENABLED", 1, 0);
        insertRole(201L, 2L, "TENANT_ADMIN", "ENABLED", 1, 0);
        insertRole(202L, 2L, "DELETED_ROLE", "ENABLED", 0, 1);
        insertUserRole(1001L, 1L, 11L, 101L);
        insertUserRole(1002L, 1L, 12L, 102L);
        insertUserRole(2001L, 2L, 21L, 201L);
        insertUserRole(2002L, 2L, 22L, 202L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 10, 20, 30);
        TenantScope.run(1L, () -> assertThat(roleMenuMapper.insertBatch(List.of(
                roleMenu(1L, 101L, 901L, 504L, createdAt)))).isEqualTo(1));
        TenantScope.run(2L, () -> assertThat(roleMenuMapper.insertBatch(List.of(
                roleMenu(2L, 202L, 901L, 506L, createdAt)))).isEqualTo(1));

        assertThat(roleMenuMapper.countByMenuId(901L)).isEqualTo(1);
        assertThat(roleMenuMapper.selectUsersAffectedByMenu(901L)).containsExactly(
                new MenuAffectedUser(1L, 11L),
                new MenuAffectedUser(1L, 12L),
                new MenuAffectedUser(2L, 21L));
        assertThat(roleMenuMapper.selectTenantAdminUsers()).containsExactly(
                new MenuAffectedUser(1L, 12L),
                new MenuAffectedUser(2L, 21L));
    }

    @Test
    void shouldQueryAndPhysicallyDeleteUserRolesWithinCurrentTenant() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 10, 20, 30);
        TenantScope.run(1L, () -> assertThat(userRoleMapper.insertBatch(List.of(
                userRole(1L, 12L, 101L, 503L, createdAt),
                userRole(1L, 11L, 102L, 503L, createdAt),
                userRole(1L, 11L, 101L, 503L, createdAt)))).isEqualTo(3));
        // 外租户重用相同关联外键，能捕获遗漏 tenant_id 的读写条件。
        TenantScope.run(2L, () -> assertThat(userRoleMapper.insertBatch(List.of(
                userRole(2L, 11L, 101L, 505L, createdAt),
                userRole(2L, 21L, 101L, 505L, createdAt)))).isEqualTo(2));
        var foreignBefore = jdbcTemplate.queryForList("SELECT * FROM sys_user_role WHERE tenant_id = 2 ORDER BY id");

        TenantScope.run(1L, () -> {
            assertThat(userRoleMapper.selectRoleIdsByUserId(1L, 11L)).containsExactly(101L, 102L);
            assertThat(userRoleMapper.selectUserIdsByRole(1L, 101L)).containsExactly(11L, 12L).doesNotHaveDuplicates();
            assertThat(userRoleMapper.countByRole(1L, 101L)).isEqualTo(2);
            assertThat(userRoleMapper.countByRole(1L, 999L)).isZero();
            assertThat(userRoleMapper.selectRoleIdsByUserId(1L, 21L)).isEmpty();
            assertThat(userRoleMapper.selectRoleIdsByUserId(2L, 11L)).isEmpty();
            assertThat(userRoleMapper.selectUserIdsByRole(2L, 101L)).isEmpty();
            assertThat(userRoleMapper.countByRole(2L, 101L)).isZero();
            assertThat(userRoleMapper.deleteByUser(2L, 11L)).isZero();
            assertThat(userRoleMapper.deleteByUser(1L, 21L)).isZero();
            assertThat(userRoleMapper.deleteByUser(1L, 11L)).isEqualTo(2);
            assertThat(userRoleMapper.deleteByUser(1L, 11L)).isZero();
            assertThat(userRoleMapper.selectRoleIdsByUserId(1L, 11L)).isEmpty();
            assertThat(userRoleMapper.selectUserIdsByRole(1L, 101L)).containsExactly(12L);
            assertThat(userRoleMapper.countByRole(1L, 101L)).isEqualTo(1);
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE tenant_id = 1 AND user_id = 11", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT * FROM sys_user_role WHERE tenant_id = 2 ORDER BY id"))
                .isEqualTo(foreignBefore);
        TenantScope.run(2L, () -> {
            assertThat(userRoleMapper.countByRole(2L, 101L)).isEqualTo(2);
            assertThat(userRoleMapper.selectUserIdsByRole(2L, 101L)).containsExactly(11L, 21L);
        });
    }

    @Test
    void shouldQueryAndPhysicallyDeleteRoleMenusWithinCurrentTenant() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 10, 20, 30);
        TenantScope.run(1L, () -> assertThat(roleMenuMapper.insertBatch(List.of(
                roleMenu(1L, 101L, 902L, 504L, createdAt),
                roleMenu(1L, 101L, 901L, 504L, createdAt),
                roleMenu(1L, 102L, 901L, 504L, createdAt)))).isEqualTo(3));
        TenantScope.run(2L, () -> assertThat(roleMenuMapper.insertBatch(List.of(
                roleMenu(2L, 101L, 901L, 506L, createdAt),
                roleMenu(2L, 201L, 903L, 506L, createdAt)))).isEqualTo(2));
        var foreignBefore = jdbcTemplate.queryForList("SELECT * FROM sys_role_menu WHERE tenant_id = 2 ORDER BY id");

        TenantScope.run(1L, () -> {
            assertThat(roleMenuMapper.selectMenuIdsByRole(1L, 101L)).containsExactly(901L, 902L);
            assertThat(roleMenuMapper.selectMenuIdsByRole(1L, 201L)).isEmpty();
            assertThat(roleMenuMapper.selectMenuIdsByRole(2L, 101L)).isEmpty();
            assertThat(roleMenuMapper.deleteByRole(2L, 101L)).isZero();
            assertThat(roleMenuMapper.deleteByRole(1L, 201L)).isZero();
            assertThat(roleMenuMapper.deleteByRole(1L, 101L)).isEqualTo(2);
            assertThat(roleMenuMapper.deleteByRole(1L, 101L)).isZero();
            assertThat(roleMenuMapper.selectMenuIdsByRole(1L, 101L)).isEmpty();
            assertThat(roleMenuMapper.selectMenuIdsByRole(1L, 102L)).containsExactly(901L);
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE tenant_id = 1 AND role_id = 101", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT * FROM sys_role_menu WHERE tenant_id = 2 ORDER BY id"))
                .isEqualTo(foreignBefore);
        TenantScope.run(2L, () -> assertThat(roleMenuMapper.selectMenuIdsByRole(2L, 101L)).containsExactly(901L));
    }

    @Test
    void shouldLoadOnlyDistinctSortedEnabledButtonPermissionsFromCurrentTenantRoles() {
        insertRole(101L, 1L, "FIRST", "ENABLED", 0, 0);
        insertRole(102L, 1L, "SECOND", "ENABLED", 0, 0);
        insertRole(103L, 1L, "DISABLED", "DISABLED", 0, 0);
        insertRole(104L, 1L, "DELETED", "ENABLED", 0, 1);
        insertRole(105L, 1L, "UNREQUESTED", "ENABLED", 0, 0);
        insertRole(201L, 2L, "FOREIGN", "ENABLED", 0, 0);
        insertMenu(901L, "BUTTON", "test:zeta", "ENABLED", 0);
        insertMenu(902L, "BUTTON", "test:alpha", "ENABLED", 0);
        insertMenu(903L, "BUTTON", "test:disabled-menu", "DISABLED", 0);
        insertMenu(904L, "BUTTON", "test:deleted-menu", "ENABLED", 1);
        insertMenu(905L, "MENU", "test:menu", "ENABLED", 0);
        insertMenu(906L, "BUTTON", null, "ENABLED", 0);
        insertMenu(907L, "BUTTON", "test:disabled-role", "ENABLED", 0);
        insertMenu(908L, "BUTTON", "test:deleted-role", "ENABLED", 0);
        insertMenu(909L, "BUTTON", "test:foreign", "ENABLED", 0);
        insertMenu(910L, "BUTTON", "test:unrequested", "ENABLED", 0);
        insertMenu(911L, "BUTTON", "test:foreign-relation", "ENABLED", 0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 10, 20, 30);
        TenantScope.run(1L, () -> assertThat(roleMenuMapper.insertBatch(List.of(
                roleMenu(1L, 101L, 901L, 504L, createdAt),
                roleMenu(1L, 101L, 902L, 504L, createdAt),
                roleMenu(1L, 102L, 901L, 504L, createdAt),
                roleMenu(1L, 101L, 903L, 504L, createdAt),
                roleMenu(1L, 101L, 904L, 504L, createdAt),
                roleMenu(1L, 101L, 905L, 504L, createdAt),
                roleMenu(1L, 101L, 906L, 504L, createdAt),
                roleMenu(1L, 103L, 907L, 504L, createdAt),
                roleMenu(1L, 104L, 908L, 504L, createdAt),
                roleMenu(1L, 201L, 909L, 504L, createdAt),
                roleMenu(1L, 105L, 910L, 504L, createdAt)))).isEqualTo(11));
        TenantScope.run(2L, () -> assertThat(roleMenuMapper.insertBatch(List.of(
                roleMenu(2L, 201L, 909L, 506L, createdAt),
                roleMenu(2L, 101L, 911L, 506L, createdAt)))).isEqualTo(2));

        TenantScope.run(1L, () -> {
            assertThat(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(1L,
                    List.of(201L, 104L, 103L, 102L, 101L, 999L))).containsExactly("test:alpha", "test:zeta");
            assertThat(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(1L, List.of(201L, 999L))).isEmpty();
            assertThat(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(2L, List.of(201L))).isEmpty();
        });
        TenantScope.run(2L, () -> assertThat(roleMenuMapper.selectEnabledPermissionCodesByRoleIds(2L,
                List.of(101L, 201L))).containsExactly("test:foreign"));
    }

    /** 构造带独立雪花主键和确定审计值的用户角色输入，XML 只负责逐字段绑定。 */
    private SystemUserRole userRole(long tenantId, long userId, long roleId, long auditorId, LocalDateTime createdAt) {
        SystemUserRole relation = new SystemUserRole();
        relation.setId(IdWorker.getId());
        relation.setTenantId(tenantId);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setCreatedBy(auditorId);
        relation.setCreatedAt(createdAt);
        return relation;
    }

    /** 构造带独立雪花主键和确定审计值的角色菜单输入。 */
    private SystemRoleMenu roleMenu(long tenantId, long roleId, long menuId, long auditorId, LocalDateTime createdAt) {
        SystemRoleMenu relation = new SystemRoleMenu();
        relation.setId(IdWorker.getId());
        relation.setTenantId(tenantId);
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        relation.setCreatedBy(auditorId);
        relation.setCreatedAt(createdAt);
        return relation;
    }

    /** 插入菜单类型、权限码和可用性各维度，沿用生产权限码唯一约束。 */
    private void insertMenu(long id, String type, String permissionCode, String status, int deleted) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu (id, name, type, permission_code, status, deleted)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, "menu-" + id, type, permissionCode, status, deleted);
    }

    /** 插入确定状态与初始认证版本的用户，用于独立推导写入结果。 */
    private void insertUser(long id, long tenantId, String username, String status, int deleted, long version) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, tenant_id, username, password_hash, display_name,
                    status, deleted, auth_version) VALUES (?, ?, ?, 'test-hash', ?, ?, ?, ?)
                """, id, tenantId, username, username, status, deleted, version);
    }

    /** 插入角色资格各维度，保留生产唯一约束。 */
    private void insertRole(long id, long tenantId, String code, String status, int builtIn, int deleted) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, built_in, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, code, code, status, builtIn, deleted);
    }

    /** 允许构造跨租户关联脏数据，验证真实联表隔离。 */
    private void insertUserRole(long id, long tenantId, long userId, long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES (?, ?, ?, ?)",
                id, tenantId, userId, roleId);
    }

    /** 绕过逻辑删除过滤读取用户持久结果，检查审计及未命中记录。 */
    private Map<String, Object> userRow(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM sys_user WHERE id = ?", id);
    }

    /** 绕过逻辑删除过滤读取角色持久结果。 */
    private Map<String, Object> roleRow(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM sys_role WHERE id = ?", id);
    }

    /** 同时检查 statement 名称与 XML 来源，防止旧注解掩盖扫描配置失效。 */
    private void assertXmlStatement(Class<?> mapperType, String method, String resource) {
        String statementId = mapperType.getName() + "." + method;
        assertThat(configuration.getMappedStatementNames()).contains(statementId);
        assertThat(configuration.getMappedStatement(statementId).getResource().replace('\\', '/'))
                .as("XML resource for %s", statementId)
                .contains(resource);
    }

    /** 插入只含必填业务字段的全局租户，其他字段沿用生产迁移默认值。 */
    private void insertTenant(long id, String code) {
        jdbcTemplate.update("INSERT INTO sys_tenant (id, tenant_code, tenant_name) VALUES (?, ?, ?)",
                id, code, code);
    }

    /** 复用生产拦截器与迁移，仅装配执行真实 Mapper SQL 所需的持久化组件。 */
    @Configuration(proxyBeanMethods = false)
    @Import({MyBatisCommonConfig.class, TenantMyBatisConfig.class})
    static class MapperTestConfiguration {

        /** 创建唯一 H2 MySQL 模式数据库并按生产脚本完成 V1 至 V4 迁移。 */
        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:mapper_xml_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .target("4").load().migrate();
            return dataSource;
        }

        /** 显式扫描生产 XML 并使用 Spring 事务，让 Mapper 调用与测试事务共享连接。 */
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor interceptor)
                throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTransactionFactory(new SpringManagedTransactionFactory());
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setPlugins(interceptor);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/**/*.xml"));
            return factory.getObject();
        }

        /** 为后续复杂查询用例注册所有生产 Mapper，排除同包的非 Mapper 接口。 */
        @Bean
        static MapperScannerConfigurer mapperScannerConfigurer() {
            MapperScannerConfigurer scanner = new MapperScannerConfigurer();
            scanner.setBasePackage("com.xtong.saas.system");
            scanner.setAnnotationClass(Mapper.class);
            scanner.setSqlSessionFactoryBeanName("sqlSessionFactory");
            return scanner;
        }
    }
}
