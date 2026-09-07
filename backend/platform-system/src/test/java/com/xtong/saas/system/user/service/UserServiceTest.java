package com.xtong.saas.system.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.mybatis.AuditorProvider;
import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.role.entity.SystemUserRole;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.user.dto.CreateUserDTO;
import com.xtong.saas.system.user.dto.ResetPasswordDTO;
import com.xtong.saas.system.user.dto.UserQueryDTO;
import com.xtong.saas.system.user.dto.UpdateUserDTO;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.exception.UserErrorCode;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import com.xtong.saas.system.user.service.impl.UserServiceImpl;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证租户用户服务的租户隔离、管理员保护、密码规则和提交后会话撤销。 */
class UserServiceTest {

    private final SystemUserMapper userMapper = mock(SystemUserMapper.class);
    private final SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
    private final SystemUserRoleMapper userRoleMapper = mock(SystemUserRoleMapper.class);
    private final SystemTenantMapper tenantMapper = mock(SystemTenantMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);
    private final AuditorProvider auditorProvider = () -> OptionalLong.of(42L);
    private final UserService service = new UserServiceImpl(
            userMapper, roleMapper, userRoleMapper, tenantMapper, passwordEncoder,
            sessionRevocationService, auditorProvider);

    @AfterEach
    void shouldClearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRejectDuplicateUsernameInCurrentTenant() {
        when(userMapper.countByTenantAndUsernameIncludingDeleted(1L, "alice")).thenReturn(1L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.create(createCommand(Set.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USERNAME_ALREADY_EXISTS));

        verify(userMapper, never()).insert(any(SystemUser.class));
    }

    @Test
    void shouldRejectUsernameReservedByLogicallyDeletedUser() {
        when(userMapper.countByTenantAndUsernameIncludingDeleted(1L, "alice")).thenReturn(1L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.create(createCommand(Set.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USERNAME_ALREADY_EXISTS));
    }

    @Test
    void shouldMapConcurrentDuplicateUsernameInsertToStableErrorCode() {
        when(userMapper.countByTenantAndUsernameIncludingDeleted(1L, "alice")).thenReturn(0L);
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt");
        doThrow(new DuplicateKeyException("duplicate username")).when(userMapper).insert(any(SystemUser.class));

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.create(createCommand(Set.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USERNAME_ALREADY_EXISTS));
    }

    @Test
    void shouldLockTenantBeforeCreatingUserRoleRelations() {
        when(userMapper.countByTenantAndUsernameIncludingDeleted(1L, "alice")).thenReturn(0L);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.countByTenantAndIds(1L, Set.of(8L))).thenReturn(1L);
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt");
        doAnswer(invocation -> {
            ((SystemUser) invocation.getArgument(0)).setId(10L);
            return 1;
        }).when(userMapper).insert(any(SystemUser.class));

        TenantScope.run(1L, () -> service.create(createCommand(Set.of(8L))));

        org.mockito.InOrder order = inOrder(tenantMapper, roleMapper, userMapper, userRoleMapper);
        order.verify(tenantMapper).lockByIdForAdminInvariant(1L);
        order.verify(roleMapper).countByTenantAndIds(1L, Set.of(8L));
        order.verify(userMapper).insert(any(SystemUser.class));
        order.verify(userRoleMapper).deleteByUser(1L, 10L);
        ArgumentCaptor<List<SystemUserRole>> relationsCaptor = ArgumentCaptor.captor();
        order.verify(userRoleMapper).insertBatch(relationsCaptor.capture());
        assertThat(relationsCaptor.getValue()).singleElement().satisfies(relation -> {
            assertThat(relation.getId()).isPositive();
            assertThat(relation.getTenantId()).isEqualTo(1L);
            assertThat(relation.getUserId()).isEqualTo(10L);
            assertThat(relation.getRoleId()).isEqualTo(8L);
            assertThat(relation.getCreatedBy()).isEqualTo(42L);
            assertThat(relation.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void shouldRejectRoleFromAnotherTenant() {
        when(roleMapper.countByTenantAndIds(1L, Set.of(99L))).thenReturn(0L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.assignRoles(10L, Set.of(99L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_ROLE_ASSIGNMENT));
    }

    @Test
    void shouldRejectDeletingCurrentUser() {
        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.delete(10L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.CANNOT_OPERATE_CURRENT_USER));

        verify(userMapper, never()).deleteById(10L);
    }

    @Test
    void shouldRevokeSessionsOnlyAfterCommittedPasswordReset() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("bcrypt");
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.resetPassword(10L, new ResetPasswordDTO("NewPassword123")));

        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
        afterCommit();
        verify(sessionRevocationService).revokeAllUserSessions(1L, 10L);
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt");
        assertThat(user.getPasswordChangedAt()).isNotNull();
    }

    @Test
    void shouldPhysicallyReplaceRolesAndRevokeSessionsAfterCommit() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.countByTenantAndIds(1L, Set.of(8L, 9L))).thenReturn(2L);
        TransactionSynchronizationManager.initSynchronization();

        LocalDateTime startedAt = LocalDateTime.now();
        TenantScope.run(1L, () -> service.assignRoles(10L, Set.of(8L, 9L)));

        verify(userRoleMapper).deleteByUser(1L, 10L);
        ArgumentCaptor<List<SystemUserRole>> relationsCaptor = ArgumentCaptor.captor();
        verify(userRoleMapper).insertBatch(relationsCaptor.capture());
        List<SystemUserRole> relations = relationsCaptor.getValue();
        assertThat(relations).hasSize(2).allSatisfy(relation -> {
            assertThat(relation.getTenantId()).isEqualTo(1L);
            assertThat(relation.getUserId()).isEqualTo(10L);
            assertThat(relation.getCreatedBy()).isEqualTo(42L);
            assertThat(relation.getCreatedAt()).isBetween(startedAt, LocalDateTime.now());
        });
        assertThat(relations).extracting(SystemUserRole::getRoleId).containsExactlyInAnyOrder(8L, 9L);
        assertThat(relations).extracting(SystemUserRole::getId).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(relations).extracting(SystemUserRole::getCreatedAt).containsOnly(relations.getFirst().getCreatedAt());
        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
        afterCommit();
        verify(sessionRevocationService).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldRemoveAllRolesWithoutInvokingEmptyBatchInsert() {
        when(userMapper.selectOne(any())).thenReturn(user(10L, UserStatus.ENABLED));
        when(userRoleMapper.selectRoleIdsByUserId(1L, 10L)).thenReturn(List.of(8L));

        TenantScope.run(1L, () -> service.assignRoles(10L, Set.of()));

        verify(userRoleMapper).deleteByUser(1L, 10L);
        verify(userRoleMapper, never()).insertBatch(anyList());
        verify(roleMapper, never()).countByTenantAndIds(anyLong(), any());
        verify(userMapper).incrementAuthVersion(1L, 10L);
    }

    @Test
    void shouldTreatIdenticalRoleAssignmentAsNoOpWithoutVersionIncrement() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.countByTenantAndIds(1L, Set.of(8L))).thenReturn(1L);
        when(userRoleMapper.selectRoleIdsByUserId(1L, 10L)).thenReturn(List.of(8L));

        TenantScope.run(1L, () -> service.assignRoles(10L, Set.of(8L)));

        verify(userRoleMapper, never()).deleteByUser(1L, 10L);
        verify(userMapper, never()).incrementAuthVersion(1L, 10L);
    }

    @Test
    void shouldNormalizeUpdatedUsernameWhileHoldingTenantLockAndIncrementVersion() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(userMapper.selectOne(any())).thenReturn(user);

        TenantScope.run(1L, () -> service.update(
                10L, new UpdateUserDTO("  ALICE.NEW  ", "Alice", null, null)));

        assertThat(user.getUsername()).isEqualTo("alice.new");
        org.mockito.InOrder order = inOrder(tenantMapper, userMapper);
        order.verify(tenantMapper).lockByIdForAdminInvariant(1L);
        order.verify(userMapper).selectOne(any());
        order.verify(userMapper).updateById(user);
        order.verify(userMapper).incrementAuthVersion(1L, 10L);
    }

    @Test
    void shouldRejectRemovingLastTenantAdminRoleUnderTenantLock() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(roleMapper.countByTenantAndIds(1L, Set.of(8L))).thenReturn(1L);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.existsTenantAdminRole(1L, 10L)).thenReturn(true);
        when(roleMapper.containsTenantAdminRole(1L, Set.of(8L))).thenReturn(false);
        when(roleMapper.countEnabledTenantAdminUsers(1L)).thenReturn(1L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.assignRoles(10L, Set.of(8L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.LAST_TENANT_ADMIN));

        org.mockito.InOrder order = inOrder(tenantMapper, userMapper);
        order.verify(tenantMapper).lockByIdForAdminInvariant(1L);
        order.verify(userMapper).selectOne(any());
        verify(userRoleMapper, never()).deleteByUser(1L, 10L);
    }

    @Test
    void shouldRejectDisablingLastEnabledTenantAdministrator() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.existsTenantAdminRole(1L, 10L)).thenReturn(true);
        when(roleMapper.countEnabledTenantAdminUsers(1L)).thenReturn(1L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.disable(10L, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.LAST_TENANT_ADMIN));

        verify(userMapper, never()).updateById(any(SystemUser.class));
        verify(tenantMapper).lockByIdForAdminInvariant(1L);
    }

    @Test
    void shouldLockTenantBeforeDeletingUserToSerializeAdminInvariant() {
        SystemUser user = user(10L, UserStatus.DISABLED);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(userMapper.selectOne(any())).thenReturn(user);

        TenantScope.run(1L, () -> service.delete(10L, 11L));

        org.mockito.InOrder order = inOrder(tenantMapper, userMapper);
        order.verify(tenantMapper).lockByIdForAdminInvariant(1L);
        order.verify(userMapper).selectOne(any());
        verify(userMapper).logicalDeleteWithAudit(eq(1L), eq(10L), anyLong(), any());
    }

    @Test
    void shouldNotRevokeSessionsWhenDisabledUserTransactionRollsBack() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.existsTenantAdminRole(1L, 10L)).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.disable(10L, 11L));

        afterRollback();
        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldKeepCommittedAuthVersionWhenBestEffortRevocationFails() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.existsTenantAdminRole(1L, 10L)).thenReturn(false);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(sessionRevocationService).revokeAllUserSessions(1L, 10L);
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.disable(10L, 11L));
        afterCommit();

        verify(userMapper).incrementAuthVersion(1L, 10L);
        verify(sessionRevocationService).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldReturnDisabledUserForPasswordVerificationBeforeStatusDisclosure() {
        SystemUser disabled = user(10L, UserStatus.DISABLED);
        when(userMapper.selectOne(any())).thenReturn(disabled);

        SystemUser result = service.requireForLogin(1L, "alice");

        assertThat(result).isSameAs(disabled);
    }

    @Test
    void shouldEnforcePageLimitAndUtf8PasswordLimitWithJakartaValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new UserQueryDTO(1, 501, null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("pageSize");
        assertThat(validator.validate(new ResetPasswordDTO("密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密码密")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("passwordWithinUtf8Limit");
    }

    @Test
    void shouldMapUserIdsToStringsWithoutPasswordHash() {
        SystemUser user = user(10L, UserStatus.ENABLED);
        user.setTenantId(1L);
        user.setPasswordHash("must-not-leak");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectRoleIdsByUserId(1L, 10L)).thenReturn(List.of(8L));

        TenantScope.run(1L, () -> {
            Object view = service.get(10L);
            assertThat(view).hasFieldOrPropertyWithValue("id", "10");
            assertThat(view).hasFieldOrPropertyWithValue("tenantId", "1");
            assertThat(view.toString()).doesNotContain("must-not-leak");
        });
    }

    private void afterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void afterRollback() {
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private CreateUserDTO createCommand(Set<Long> roleIds) {
        return new CreateUserDTO("alice", "Alice", "Password123", "alice@example.com", "13800000000", roleIds);
    }

    private SystemUser user(long id, UserStatus status) {
        SystemUser user = new SystemUser();
        user.setId(id);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setStatus(status);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
