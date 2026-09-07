package com.xtong.saas.system.migration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.xtong.saas.common.mybatis.MyBatisCommonConfig;
import com.xtong.saas.system.bootstrap.mapper.SystemBootstrapLockMapper;
import com.xtong.saas.system.tenant.config.TenantMyBatisConfig;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 在 H2 MySQL 模式中验证 Mapper XML 的加载、参数绑定和租户边界。 */
class MapperXmlIntegrationTest {

    private AnnotationConfigApplicationContext context;
    private org.apache.ibatis.session.Configuration configuration;
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private SystemBootstrapLockMapper bootstrapLockMapper;
    private SystemTenantMapper tenantMapper;

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
                "mapper/system/bootstrap/SystemBootstrapLockMapper.xml");
        assertXmlStatement(SystemTenantMapper.class, "lockByIdForAdminInvariant",
                "mapper/system/tenant/SystemTenantMapper.xml");
        assertXmlStatement(SystemTenantMapper.class, "lockByIdAndCodeForAuthentication",
                "mapper/system/tenant/SystemTenantMapper.xml");
        assertXmlStatement(SystemTenantMapper.class, "lockByIdForAuthentication",
                "mapper/system/tenant/SystemTenantMapper.xml");
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
