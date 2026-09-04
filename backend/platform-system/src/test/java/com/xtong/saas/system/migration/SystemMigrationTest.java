package com.xtong.saas.system.migration;

import org.flywaydb.core.Flyway;
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
            "system:menu:tree",
            "system:permission:list");

    @Test
    void shouldCreateSystemSchemaAndPermissionCatalog() throws Exception {
        DataSource dataSource = migrationDataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);

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
