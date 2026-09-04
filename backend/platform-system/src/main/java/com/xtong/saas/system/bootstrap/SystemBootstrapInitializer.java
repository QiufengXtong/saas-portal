package com.xtong.saas.system.bootstrap;

import com.xtong.saas.system.bootstrap.config.BootstrapProperties;
import com.xtong.saas.system.bootstrap.exception.BootstrapConfigurationException;
import com.xtong.saas.system.role.entity.SystemRole;
import com.xtong.saas.system.role.entity.SystemUserRole;
import com.xtong.saas.system.role.enums.RoleStatus;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.tenant.enums.TenantStatus;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.tenant.service.TenantService;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 在空租户库中以单事务创建首租户、内置管理员角色、管理员及其关联。 */
@Component
@ConditionalOnBean(BootstrapProperties.class)
public class SystemBootstrapInitializer implements ApplicationRunner {

    private static final Object BOOTSTRAP_MONITOR = new Object();
    private static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private final BootstrapProperties properties;
    private final TenantService tenantService;
    private final SystemTenantMapper tenantMapper;
    private final SystemRoleMapper roleMapper;
    private final SystemUserMapper userMapper;
    private final SystemUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public SystemBootstrapInitializer(
            BootstrapProperties properties,
            TenantService tenantService,
            SystemTenantMapper tenantMapper,
            SystemRoleMapper roleMapper,
            SystemUserMapper userMapper,
            SystemUserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.tenantService = tenantService;
        this.tenantMapper = tenantMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 由 Spring 在 Flyway 和应用上下文初始化完成后触发首租户引导。 */
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        initializeIfNecessary();
    }

    /** 为集成验证和受控重复执行提供同一幂等初始化入口。 */
    @Transactional
    public void run() {
        initializeIfNecessary();
    }

    private void initializeIfNecessary() {
        synchronized (BOOTSTRAP_MONITOR) {
            if (tenantService.hasAnyTenant()) {
                return;
            }
            ValidBootstrapConfiguration configuration = validateConfiguration();
            createInitialTenant(configuration);
        }
    }

    private void createInitialTenant(ValidBootstrapConfiguration configuration) {
        SystemTenant tenant = new SystemTenant();
        tenant.setTenantCode(configuration.tenantCode());
        tenant.setTenantName(configuration.tenantName());
        tenant.setStatus(TenantStatus.ENABLED);
        tenantMapper.insert(tenant);
        requireAssignedId(tenant.getId(), "tenant");
        tenantMapper.lockByIdForAdminInvariant(tenant.getId());

        TenantScope.run(tenant.getId(), () -> createAdminGraph(tenant.getId(), configuration));
    }

    private void createAdminGraph(long tenantId, ValidBootstrapConfiguration configuration) {
        SystemRole role = new SystemRole();
        role.setTenantId(tenantId);
        role.setRoleCode(TENANT_ADMIN_ROLE_CODE);
        role.setRoleName("租户管理员");
        role.setStatus(RoleStatus.ENABLED);
        role.setBuiltIn(true);
        roleMapper.insert(role);
        requireAssignedId(role.getId(), "role");

        SystemUser user = new SystemUser();
        user.setTenantId(tenantId);
        user.setUsername(configuration.adminUsername());
        user.setDisplayName(configuration.adminUsername());
        user.setPasswordHash(passwordEncoder.encode(configuration.adminPassword()));
        user.setStatus(UserStatus.ENABLED);
        user.setPasswordChangedAt(LocalDateTime.now());
        userMapper.insert(user);
        requireAssignedId(user.getId(), "user");

        SystemUserRole relation = new SystemUserRole();
        relation.setTenantId(tenantId);
        relation.setUserId(user.getId());
        relation.setRoleId(role.getId());
        userRoleMapper.insert(relation);
    }

    private ValidBootstrapConfiguration validateConfiguration() {
        List<String> invalidProperties = new ArrayList<>();
        requireText(properties.tenantCode(), "saas.bootstrap.tenant-code", invalidProperties);
        requireText(properties.tenantName(), "saas.bootstrap.tenant-name", invalidProperties);
        requireText(properties.adminUsername(), "saas.bootstrap.admin-username", invalidProperties);
        requirePassword(properties.adminPassword(), invalidProperties);
        if (!invalidProperties.isEmpty()) {
            throw new BootstrapConfigurationException(
                    "缺少或无效的首次初始化配置: " + String.join(", ", invalidProperties));
        }
        return new ValidBootstrapConfiguration(
                normalizeIdentity(properties.tenantCode()),
                properties.tenantName().strip(),
                normalizeIdentity(properties.adminUsername()),
                properties.adminPassword());
    }

    private static void requireText(String value, String propertyName, List<String> invalidProperties) {
        if (value == null || value.isBlank()) {
            invalidProperties.add(propertyName);
        }
    }

    private static void requirePassword(String password, List<String> invalidProperties) {
        if (password == null
                || password.isBlank()
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            invalidProperties.add("saas.bootstrap.admin-password");
        }
    }

    private static String normalizeIdentity(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static void requireAssignedId(Long id, String entityName) {
        if (id == null) {
            throw new IllegalStateException("Bootstrap " + entityName + " id was not assigned");
        }
    }

    /** 保存已校验和规范化的引导数据，密码保持调用方原始字符序列。 */
    private record ValidBootstrapConfiguration(
            String tenantCode,
            String tenantName,
            String adminUsername,
            String adminPassword) {
    }
}
