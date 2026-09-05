package com.xtong.saas.system.bootstrap;

import com.xtong.saas.system.bootstrap.config.BootstrapProperties;
import com.xtong.saas.system.bootstrap.exception.BootstrapConfigurationException;
import com.xtong.saas.system.bootstrap.mapper.SystemBootstrapLockMapper;
import com.xtong.saas.system.role.entity.SystemRole;
import com.xtong.saas.system.role.enums.RoleStatus;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.tenant.enums.TenantStatus;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.tenant.service.TenantService;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证首租户管理员初始化的配置安全、租户锁、关联完整性和重复执行幂等性。 */
@ExtendWith(MockitoExtension.class)
class SystemBootstrapInitializerTest {

    @Mock
    private TenantService tenantService;
    @Mock
    private SystemBootstrapLockMapper bootstrapLockMapper;
    @Mock
    private SystemTenantMapper tenantMapper;
    @Mock
    private SystemRoleMapper roleMapper;
    @Mock
    private SystemUserMapper userMapper;
    @Mock
    private SystemUserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private void stubGeneratedIds() {
        doAnswer(invocation -> {
            ((SystemTenant) invocation.getArgument(0)).setId(101L);
            return 1;
        }).when(tenantMapper).insert(any(SystemTenant.class));
        doAnswer(invocation -> {
            ((SystemRole) invocation.getArgument(0)).setId(202L);
            return 1;
        }).when(roleMapper).insert(any(SystemRole.class));
        doAnswer(invocation -> {
            ((SystemUser) invocation.getArgument(0)).setId(303L);
            return 1;
        }).when(userMapper).insert(any(SystemUser.class));
    }

    @Test
    void shouldInitializeCompleteAdminGraphOnceAndReuseTenantInvariantLock() {
        stubGeneratedIds();
        BootstrapProperties properties = new BootstrapProperties(
                " Default ", " Default Tenant ", " ADMIN ", "  Secret123  ");
        SystemBootstrapInitializer initializer = initializer(properties);
        when(tenantService.hasAnyTenant()).thenReturn(false, true);
        when(passwordEncoder.encode("  Secret123  ")).thenReturn("bcrypt-hash");

        initializer.run();
        initializer.run();

        ArgumentCaptor<SystemTenant> tenantCaptor = ArgumentCaptor.forClass(SystemTenant.class);
        ArgumentCaptor<SystemRole> roleCaptor = ArgumentCaptor.forClass(SystemRole.class);
        ArgumentCaptor<SystemUser> userCaptor = ArgumentCaptor.forClass(SystemUser.class);
        verify(tenantMapper, times(1)).insert(tenantCaptor.capture());
        verify(roleMapper, times(1)).insert(roleCaptor.capture());
        verify(userMapper, times(1)).insert(userCaptor.capture());
        verify(userRoleMapper, times(1)).insertBatch(eq(101L), eq(303L), eq(Set.of(202L)), eq(0L), any());

        assertThat(tenantCaptor.getValue().getTenantCode()).isEqualTo("default");
        assertThat(tenantCaptor.getValue().getTenantName()).isEqualTo("Default Tenant");
        assertThat(tenantCaptor.getValue().getStatus()).isEqualTo(TenantStatus.ENABLED);
        assertThat(roleCaptor.getValue().getTenantId()).isEqualTo(101L);
        assertThat(roleCaptor.getValue().getRoleCode()).isEqualTo("TENANT_ADMIN");
        assertThat(roleCaptor.getValue().getRoleName()).isEqualTo("租户管理员");
        assertThat(roleCaptor.getValue().getStatus()).isEqualTo(RoleStatus.ENABLED);
        assertThat(roleCaptor.getValue().getBuiltIn()).isTrue();
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo(101L);
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("admin");
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("admin");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ENABLED);
        assertThat(userCaptor.getValue().getPasswordChangedAt()).isNotNull();
        assertThat(TenantContextHolder.currentTenantId()).isEmpty();

        InOrder order = inOrder(bootstrapLockMapper, tenantService, tenantMapper, roleMapper, userMapper, userRoleMapper);
        order.verify(bootstrapLockMapper).lockInitialization();
        order.verify(tenantService).hasAnyTenant();
        order.verify(tenantMapper).insert(any(SystemTenant.class));
        order.verify(tenantMapper).lockByIdForAdminInvariant(101L);
        order.verify(roleMapper).insert(any(SystemRole.class));
        order.verify(userMapper).insert(any(SystemUser.class));
        order.verify(userRoleMapper).insertBatch(eq(101L), eq(303L), eq(Set.of(202L)), eq(0L), any());
        order.verify(bootstrapLockMapper).lockInitialization();
        order.verify(tenantService).hasAnyTenant();
    }

    @Test
    void shouldSkipConfigurationValidationWhenTenantAlreadyExists() {
        SystemBootstrapInitializer initializer = initializer(new BootstrapProperties(null, null, null, null));
        when(tenantService.hasAnyTenant()).thenReturn(true);

        initializer.run();

        verify(bootstrapLockMapper).lockInitialization();
        verifyNoInteractions(tenantMapper, roleMapper, userMapper, userRoleMapper, passwordEncoder);
    }

    @Test
    void shouldTreatConcurrentTenantUniqueKeyWinnerAsIdempotentCompletion() {
        SystemBootstrapInitializer initializer = initializer(new BootstrapProperties(
                "default", "Default Tenant", "admin", "Secret123"));
        when(tenantService.hasAnyTenant()).thenReturn(false, true);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("concurrent tenant winner"))
                .when(tenantMapper).insert(any(SystemTenant.class));

        initializer.run();

        verify(bootstrapLockMapper).lockInitialization();
        verify(tenantService, times(2)).hasAnyTenant();
        verifyNoInteractions(roleMapper, userMapper, userRoleMapper, passwordEncoder);
    }

    @ParameterizedTest(name = "missing {1}")
    @MethodSource("missingProperties")
    void shouldNameMissingConfigurationWithoutLeakingProvidedPassword(
            BootstrapProperties properties, String missingProperty) {
        SystemBootstrapInitializer initializer = initializer(properties);
        when(tenantService.hasAnyTenant()).thenReturn(false);

        assertThatThrownBy(initializer::run)
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessageContaining(missingProperty)
                .hasMessageNotContaining("NeverExposeThisPassword");
        verify(bootstrapLockMapper).lockInitialization();
        verifyNoInteractions(tenantMapper, roleMapper, userMapper, userRoleMapper, passwordEncoder);
    }

    @Test
    void shouldRejectPasswordOutsideBcryptByteLimitWithoutIncludingItsValue() {
        String oversizedPassword = "密".repeat(25);
        SystemBootstrapInitializer initializer = initializer(new BootstrapProperties(
                "default", "Default Tenant", "admin", oversizedPassword));
        when(tenantService.hasAnyTenant()).thenReturn(false);

        assertThat(oversizedPassword.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(72);
        assertThatThrownBy(initializer::run)
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessageContaining("saas.bootstrap.admin-password")
                .hasMessageNotContaining(oversizedPassword);
        verify(bootstrapLockMapper).lockInitialization();
        verify(passwordEncoder, never()).encode(oversizedPassword);
    }

    private SystemBootstrapInitializer initializer(BootstrapProperties properties) {
        return new SystemBootstrapInitializer(
                properties,
                bootstrapLockMapper,
                tenantService,
                tenantMapper,
                roleMapper,
                userMapper,
                userRoleMapper,
                passwordEncoder);
    }

    private static Stream<Arguments> missingProperties() {
        String password = "NeverExposeThisPassword";
        return Stream.of(
                Arguments.of(new BootstrapProperties(null, "Tenant", "admin", password),
                        "saas.bootstrap.tenant-code"),
                Arguments.of(new BootstrapProperties("default", " ", "admin", password),
                        "saas.bootstrap.tenant-name"),
                Arguments.of(new BootstrapProperties("default", "Tenant", null, password),
                        "saas.bootstrap.admin-username"),
                Arguments.of(new BootstrapProperties("default", "Tenant", "admin", " "),
                        "saas.bootstrap.admin-password"));
    }
}
