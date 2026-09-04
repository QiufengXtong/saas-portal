package com.xtong.saas;

import com.xtong.saas.system.bootstrap.SystemBootstrapInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证数据库迁移及首租户管理员初始化在真实 Boot 上下文中正确且幂等。 */
@SpringBootTest
@ActiveProfiles("test")
class SystemBootstrapIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemBootstrapInitializer initializer;

    @Test
    void shouldMigrateAndBootstrapInitialTenantIdempotently() {
        assertThat(successfulVersionedMigrationCount()).isEqualTo(3);
        assertThat(count("sys_tenant")).isEqualTo(1);
        assertThat(count("sys_user")).isEqualTo(1);
        assertThat(count("sys_role")).isEqualTo(1);
        assertThat(count("sys_user_role")).isEqualTo(1);

        assertThat(singleString("SELECT tenant_code FROM sys_tenant")).isEqualTo("test");
        assertThat(singleString("SELECT username FROM sys_user")).isEqualTo("admin");
        assertThat(singleString("SELECT role_code FROM sys_role")).isEqualTo("TENANT_ADMIN");

        initializer.run();

        assertThat(count("sys_tenant")).isEqualTo(1);
        assertThat(count("sys_user")).isEqualTo(1);
        assertThat(count("sys_role")).isEqualTo(1);
        assertThat(count("sys_user_role")).isEqualTo(1);
    }

    private long count(String tableName) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return result == null ? 0 : result;
    }

    private String singleString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private long successfulVersionedMigrationCount() {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success = TRUE",
                Long.class);
        return result == null ? 0 : result;
    }
}
