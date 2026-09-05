package com.xtong.saas;

import com.xtong.saas.system.bootstrap.exception.BootstrapConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** 验证真实 Boot 配置绑定在无外部 Bootstrap 变量时安全失败且不创建可预测管理员。 */
class MissingBootstrapConfigurationApplicationTest {

    private static final String DATABASE_URL =
            "jdbc:h2:mem:missing_bootstrap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test
    void shouldRejectMissingBootstrapValuesWithSafePropertyNamesOnly() throws SQLException {
        SpringApplication application = new SpringApplication(
                SaasPortalApplication.class,
                BootRedisIsolationConfiguration.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);

        ConfigurableApplicationContext[] startedContext = new ConfigurableApplicationContext[1];
        Throwable thrown = catchThrowable(() -> startedContext[0] = application.run(
                "--server.port=0",
                "--spring.datasource.url=" + DATABASE_URL,
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.hikari.initialization-fail-timeout=1",
                "--spring.datasource.hikari.connection-timeout=10000",
                "--spring.flyway.enabled=true",
                "--spring.flyway.locations=classpath:db/migration",
                "--saas.auth.jwt-secret=test-only-secret-with-at-least-32-bytes"));
        if (startedContext[0] != null) {
            startedContext[0].close();
        }

        Throwable rootCause = rootCause(thrown);
        assertThat(rootCause).isInstanceOf(BootstrapConfigurationException.class);
        assertThat(rootCause.getMessage())
                .isEqualTo("缺少或无效的首次初始化配置: "
                        + "saas.bootstrap.tenant-code, saas.bootstrap.tenant-name, "
                        + "saas.bootstrap.admin-username, saas.bootstrap.admin-password")
                .doesNotContain("default", "Default Tenant", "change_me_now", "TestPassword123");
        assertThat(countRows("sys_tenant")).isZero();
        assertThat(countRows("sys_user")).isZero();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static long countRows(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
