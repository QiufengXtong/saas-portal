package com.xtong.saas.system.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 System Flyway 迁移可从空库建立租户边界、约束索引和固定权限目录。
 */
class SystemMigrationTest {

    private static final Set<String> EXPECTED_PERMISSIONS = Set.of(
            "system:user:list",
            "system:user:detail",
            "system:user:create",
            "system:user:update",
            "system:user:enable",
            "system:user:disable",
            "system:user:reset-password",
            "system:user:delete",
            "system:user:assign-role",
            "system:role:list",
            "system:role:detail",
            "system:role:create",
            "system:role:update",
            "system:role:enable",
            "system:role:disable",
            "system:role:delete",
            "system:role:assign-menu",
            "system:menu:list",
            "system:menu:detail",
            "system:menu:create",
            "system:menu:update",
            "system:menu:enable",
            "system:menu:disable",
            "system:menu:delete",
            "system:menu:tree",
            "system:permission:list");

    @Test
    void shouldPromoteOnlyInitialAdminAndRemovePlatformRoleAssignmentsOnUpgrade() throws Exception {
        DataSource dataSource = migrationDataSource("platform_scope_upgrade");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("5")).load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into sys_tenant (id, tenant_code, tenant_name) values "
                    + "(1, 'first', 'First'), (2, 'second', 'Second')");
            statement.executeUpdate("insert into sys_user "
                    + "(id, tenant_id, username, password_hash, display_name, status) values "
                    + "(11, 1, 'admin', 'hash', 'Admin', 'ENABLED'), "
                    + "(21, 2, 'admin', 'hash', 'Admin', 'ENABLED')");
            statement.executeUpdate("insert into sys_role "
                    + "(id, tenant_id, role_code, role_name, status, built_in) values "
                    + "(101, 1, 'TENANT_ADMIN', 'Admin', 'ENABLED', 1), "
                    + "(201, 2, 'TENANT_ADMIN', 'Admin', 'ENABLED', 1)");
            statement.executeUpdate("insert into sys_user_role (id, tenant_id, user_id, role_id) values "
                    + "(1001, 1, 11, 101), (2001, 2, 21, 201)");
            statement.executeUpdate("insert into sys_role_menu (id, tenant_id, role_id, menu_id) values "
                    + "(1002, 1, 101, 13001), (2002, 2, 201, 13001)");
        }

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(queryForInt(connection, "select count(*) from sys_user_role ur "
                    + "join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id "
                    + "where ur.user_id = 11 and r.role_code = 'PLATFORM_ADMIN' and r.built_in = 1")).isEqualTo(1);
            assertThat(queryForInt(connection, "select count(*) from sys_user_role ur "
                    + "join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id "
                    + "where ur.user_id = 21 and r.role_code = 'PLATFORM_ADMIN'")).isZero();
            assertThat(queryForInt(connection, "select count(*) from sys_role_menu")).isZero();
            assertThat(queryForInt(connection, "select sum(auth_version) from sys_user")).isEqualTo(6);
        }
    }

    @Test
    void shouldRetireLegacyTenantAdminWithoutChangingAssignments() throws Exception {
        DataSource dataSource = migrationDataSource("retire_tenant_admin");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7")).load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into sys_tenant (id, tenant_code, tenant_name) values (1, 'legacy', 'Legacy')");
            statement.executeUpdate("insert into sys_user "
                    + "(id, tenant_id, username, password_hash, display_name, auth_version) values "
                    + "(11, 1, 'legacy', 'hash', 'Legacy', 5)");
            statement.executeUpdate("insert into sys_role "
                    + "(id, tenant_id, role_code, role_name, built_in) values "
                    + "(101, 1, 'TENANT_ADMIN', 'Tenant admin', 1), "
                    + "(102, 1, 'PLATFORM_ADMIN', 'Platform admin', 1)");
            statement.executeUpdate("insert into sys_user_role (id, tenant_id, user_id, role_id) values "
                    + "(1001, 1, 11, 101)");
            statement.executeUpdate("insert into sys_role_menu (id, tenant_id, role_id, menu_id) values "
                    + "(1002, 1, 101, 11001)");
        }

        assertThat(Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8")).load().migrate().migrationsExecuted).isEqualTo(1);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(queryForInt(connection, "select built_in from sys_role where id = 101")).isZero();
            assertThat(queryForInt(connection, "select built_in from sys_role where id = 102")).isEqualTo(1);
            assertThat(queryForInt(connection, "select count(*) from sys_role")).isEqualTo(2);
            assertThat(queryForInt(connection, "select count(*) from sys_user_role "
                    + "where tenant_id = 1 and user_id = 11 and role_id = 101")).isEqualTo(1);
            assertThat(queryForInt(connection, "select count(*) from sys_role_menu "
                    + "where tenant_id = 1 and role_id = 101 and menu_id = 11001")).isEqualTo(1);
            assertThat(queryForInt(connection, "select auth_version from sys_user where id = 11")).isEqualTo(6);
        }
    }

    @Test
    void shouldMoveQueryPermissionsUnderRoleMenuOnUpgrade() throws Exception {
        DataSource dataSource = migrationDataSource("move_query_permissions");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8")).load().migrate();
        try (Connection connection = dataSource.getConnection()) {
            assertThat(queryForInt(connection, "select count(*) from sys_menu "
                    + "where id in (10001, 10002) and parent_id = 100")).isEqualTo(2);
        }
        assertThat(Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .load().migrate().migrationsExecuted).isEqualTo(1);
        try (Connection connection = dataSource.getConnection()) {
            assertThat(queryForInt(connection, "select count(*) from sys_menu "
                    + "where id in (10001, 10002) and parent_id = 120 "
                    + "and built_in = 1 and permission_scope = 'TENANT' and type = 'BUTTON'")).isEqualTo(2);
            assertThat(queryForInt(connection, "select sort_order from sys_menu where id = 10001")).isEqualTo(9);
            assertThat(queryForInt(connection, "select sort_order from sys_menu where id = 10002")).isEqualTo(10);
            assertThat(queryForStrings(connection, "select permission_code from sys_menu where id in (10001, 10002)"))
                    .containsExactlyInAnyOrder("system:menu:tree", "system:permission:list");
        }
    }

    @Test
    void shouldCreateSystemSchemaAndPermissionCatalog() throws Exception {
        DataSource dataSource = migrationDataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(9);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(queryForInt(connection,
                    "select count(*) from information_schema.tables "
                            + "where table_schema = 'public' and table_name like 'sys_%'"))
                    .isEqualTo(7);
            assertThat(queryForStrings(connection,
                    "select permission_code from sys_menu where permission_code is not null"))
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_PERMISSIONS);
            assertThat(queryForInt(connection, "select count(*) from sys_tenant")).isZero();
            assertThat(queryForInt(connection, "select count(*) from sys_user")).isZero();
            assertThat(queryForInt(connection,
                    "select count(*) from information_schema.columns where table_schema = 'public' "
                            + "and table_name = 'sys_user' and column_name = 'auth_version'"))
                    .isEqualTo(1);
            assertThat(queryForInt(connection,
                    "select count(*) from information_schema.columns where table_schema = 'public' "
                            + "and table_name = 'sys_menu' and column_name = 'built_in'"))
                    .isEqualTo(1);
            assertThat(queryForInt(connection,
                    "select count(*) from information_schema.columns where table_schema = 'public' "
                            + "and table_name = 'sys_user' and column_name = 'platform_admin'"))
                    .isZero();
            assertThat(queryForInt(connection,
                    "select count(*) from information_schema.columns where table_schema = 'public' "
                            + "and table_name = 'sys_menu' and column_name = 'permission_scope'"))
                    .isEqualTo(1);
            assertThat(queryForInt(connection,
                    "select count(*) from sys_menu where permission_scope = 'PLATFORM'"))
                    .isEqualTo(8);
            assertThat(queryForInt(connection, "select count(*) from sys_menu where built_in = 0"))
                    .isZero();
        }
    }

    @Test
    void shouldSerializeBootstrapAcrossDatabaseTransactions() throws Exception {
        DataSource dataSource = migrationDataSource("bootstrap_lock");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            assertThat(queryForInt(first, "select id from sys_bootstrap_lock where id = 1 for update"))
                    .isEqualTo(1);
            CountDownLatch attempting = new CountDownLatch(1);
            Future<Integer> secondLock = executor.submit(() -> {
                attempting.countDown();
                return queryForInt(second, "select id from sys_bootstrap_lock where id = 1 for update");
            });

            assertThat(attempting.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100L);
            assertThat(secondLock.isDone()).isFalse();
            first.commit();
            assertThat(secondLock.get(1, TimeUnit.SECONDS)).isEqualTo(1);
            second.commit();
        }
    }

    @Test
    void shouldSerializeAuthenticationAndManagementOnSameTenantRow() throws Exception {
        DataSource dataSource = migrationDataSource("tenant_auth_lock");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        try (Connection setup = dataSource.getConnection(); Statement statement = setup.createStatement()) {
            statement.executeUpdate("insert into sys_tenant (id, tenant_code, tenant_name) values (1, 'acme', 'Acme')");
        }

        try (Connection login = dataSource.getConnection();
             Connection management = dataSource.getConnection();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            login.setAutoCommit(false);
            management.setAutoCommit(false);
            assertThat(queryForInt(login, "select id from sys_tenant where id = 1 for update")).isEqualTo(1);
            CountDownLatch attempting = new CountDownLatch(1);
            Future<Integer> managementLock = executor.submit(() -> {
                attempting.countDown();
                return queryForInt(management, "select id from sys_tenant where id = 1 for update");
            });

            assertThat(attempting.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100L);
            assertThat(managementLock.isDone()).isFalse();
            login.commit();
            assertThat(managementLock.get(1, TimeUnit.SECONDS)).isEqualTo(1);
            management.commit();
        }
    }

    @Test
    void shouldModelGlobalTenantAndPhysicalAssociationBoundaries() throws Exception {
        DataSource dataSource = migrationDataSource("boundaries");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(tablesWithColumn(connection, "tenant_id"))
                    .containsExactlyInAnyOrder("sys_user", "sys_role", "sys_user_role", "sys_role_menu")
                    .doesNotContain("sys_tenant", "sys_menu");
            assertThat(tablesWithColumn(connection, "deleted"))
                    .containsExactlyInAnyOrder("sys_tenant", "sys_user", "sys_role", "sys_menu")
                    .doesNotContain("sys_user_role", "sys_role_menu");
        }
    }

    @Test
    void shouldCreateNamedUniqueConstraintsAndQueryIndexes() throws Exception {
        DataSource dataSource = migrationDataSource("indexes");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(queryForStrings(connection,
                    "select constraint_name from information_schema.table_constraints "
                            + "where constraint_schema = 'public' and constraint_type = 'UNIQUE'"))
                    .contains(
                            "uk_sys_tenant_code",
                            "uk_sys_user_tenant_username",
                            "uk_sys_role_tenant_code",
                            "uk_sys_menu_permission",
                            "uk_sys_user_role",
                            "uk_sys_role_menu");
            assertThat(queryForStrings(connection,
                    "select index_name from information_schema.indexes where index_schema = 'public'"))
                    .contains(
                            "idx_sys_user_tenant_status",
                            "idx_user_role_role",
                            "idx_sys_role_tenant_status",
                            "idx_sys_menu_parent_sort");
        }
    }

    private static DataSource migrationDataSource() {
        return migrationDataSource("catalog");
    }

    private static DataSource migrationDataSource(String databaseName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static Set<String> tablesWithColumn(Connection connection, String columnName) throws SQLException {
        return queryForStrings(connection,
                "select table_name from information_schema.columns "
                        + "where table_schema = 'public' and column_name = '" + columnName + "' "
                        + "and table_name like 'sys_%'");
    }

    private static int queryForInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static Set<String> queryForStrings(Connection connection, String sql) throws SQLException {
        Set<String> values = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }
}
