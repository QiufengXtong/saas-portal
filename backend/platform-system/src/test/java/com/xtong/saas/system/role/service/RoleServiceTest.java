package com.xtong.saas.system.role.service;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.role.entity.SystemRole;
import com.xtong.saas.system.role.enums.RoleStatus;
import com.xtong.saas.system.role.exception.RoleErrorCode;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.role.service.impl.RoleServiceImpl;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证角色服务严格限定租户边界、保护内置管理员角色并在提交后撤销受影响会话。 */
class RoleServiceTest {

    private final SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
    private final SystemUserRoleMapper userRoleMapper = mock(SystemUserRoleMapper.class);
    private final SystemRoleMenuMapper roleMenuMapper = mock(SystemRoleMenuMapper.class);
    private final SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
    private final SystemTenantMapper tenantMapper = mock(SystemTenantMapper.class);
    private final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);
    private final RoleService service = new RoleServiceImpl(
            roleMapper, userRoleMapper, roleMenuMapper, menuMapper, tenantMapper, sessionRevocationService);

    @AfterEach
    void shouldClearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRejectDeletingRoleWithAssignedUsers() {
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(userRoleMapper.countByRole(1L, 8L)).thenReturn(2L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RoleErrorCode.ROLE_IN_USE));

        verify(roleMapper, never()).deleteById(8L);
    }

    @Test
    void shouldLockTenantBeforeCheckingRoleAssociationsForDelete() {
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(userRoleMapper.countByRole(1L, 8L)).thenReturn(2L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RoleErrorCode.ROLE_IN_USE));

        org.mockito.InOrder order = inOrder(tenantMapper, roleMapper, userRoleMapper);
        order.verify(tenantMapper).lockByIdForAdminInvariant(1L);
        order.verify(roleMapper).selectOne(any());
        order.verify(userRoleMapper).countByRole(1L, 8L);
    }

    @Test
    void shouldRejectRoleReturnedOutsideCurrentTenantBoundary() {
        SystemRole foreignRole = role(8L, false);
        foreignRole.setTenantId(2L);
        when(roleMapper.selectOne(any())).thenReturn(foreignRole);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.get(8L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RoleErrorCode.ROLE_NOT_FOUND));
    }

    @Test
    void shouldRejectMutatingBuiltInRole() {
        when(roleMapper.selectOne(any())).thenReturn(role(8L, true));

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.disable(8L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RoleErrorCode.BUILT_IN_ROLE_PROTECTED));

        verify(roleMapper, never()).updateById(any(SystemRole.class));
    }

    @Test
    void shouldRejectAssigningMenusToTenantAdminByCodeEvenWhenBuiltInFlagIsCorrupt() {
        SystemRole tenantAdmin = role(8L, false);
        tenantAdmin.setRoleCode("TENANT_ADMIN");
        when(roleMapper.selectOne(any())).thenReturn(tenantAdmin);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.assignMenus(8L, Set.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RoleErrorCode.BUILT_IN_ROLE_PROTECTED));

        verify(roleMenuMapper, never()).deleteByRole(1L, 8L);
    }

    @Test
    void shouldReplaceMenusAndRevokeAffectedUsersOnlyAfterCommit() {
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(menuMapper.countEnabledByIds(Set.of(101L, 102L))).thenReturn(2L);
        when(userRoleMapper.selectUserIdsByRole(1L, 8L)).thenReturn(List.of(10L, 11L));
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.assignMenus(8L, Set.of(101L, 102L)));

        verify(roleMenuMapper).deleteByRole(1L, 8L);
        verify(roleMenuMapper).insertBatch(1L, 8L, Set.of(101L, 102L));
        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
        afterCommit();
        verify(sessionRevocationService).revokeAllUserSessions(1L, 10L);
        verify(sessionRevocationService).revokeAllUserSessions(1L, 11L);
    }

    @Test
    void shouldLockTenantBeforeReplacingMenus() {
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(menuMapper.countEnabledByIds(Set.of(101L))).thenReturn(1L);
        when(userRoleMapper.selectUserIdsByRole(1L, 8L)).thenReturn(List.of());

        TenantScope.run(1L, () -> service.assignMenus(8L, Set.of(101L)));

        org.mockito.InOrder order = inOrder(tenantMapper, roleMapper, menuMapper, userRoleMapper, roleMenuMapper);
        order.verify(tenantMapper).lockByIdForAdminInvariant(1L);
        order.verify(roleMapper).selectOne(any());
        order.verify(menuMapper).countEnabledByIds(Set.of(101L));
        order.verify(userRoleMapper).selectUserIdsByRole(1L, 8L);
        order.verify(roleMenuMapper).deleteByRole(1L, 8L);
        order.verify(roleMenuMapper).insertBatch(1L, 8L, Set.of(101L));
    }

    @Test
    void shouldNotRevokeSessionsWhenMenuAssignmentRollsBack() {
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(menuMapper.countEnabledByIds(Set.of(101L))).thenReturn(1L);
        when(userRoleMapper.selectUserIdsByRole(1L, 8L)).thenReturn(List.of(10L));
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.assignMenus(8L, Set.of(101L)));

        afterRollback();
        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldRevokeAffectedUsersOnlyAfterCommittedRoleDisable() {
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(userRoleMapper.selectUserIdsByRole(1L, 8L)).thenReturn(List.of(10L));
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.disable(8L));

        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
        afterCommit();
        verify(sessionRevocationService).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldNotRevokeAffectedUsersWhenRoleDisableRollsBack() {
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(userRoleMapper.selectUserIdsByRole(1L, 8L)).thenReturn(List.of(10L));
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.disable(8L));

        afterRollback();
        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldRevokeAffectedUsersOnlyAfterCommittedRoleEnable() {
        SystemRole disabledRole = role(8L, false);
        disabledRole.setStatus(RoleStatus.DISABLED);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(disabledRole);
        when(userRoleMapper.selectUserIdsByRole(1L, 8L)).thenReturn(List.of(10L));
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.enable(8L));

        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
        afterCommit();
        verify(sessionRevocationService).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldNotRevokeAffectedUsersWhenRoleEnableRollsBack() {
        SystemRole disabledRole = role(8L, false);
        disabledRole.setStatus(RoleStatus.DISABLED);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(disabledRole);
        when(userRoleMapper.selectUserIdsByRole(1L, 8L)).thenReturn(List.of(10L));
        TransactionSynchronizationManager.initSynchronization();

        TenantScope.run(1L, () -> service.enable(8L));

        afterRollback();
        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldNotRevokeSessionsWhenRoleStatusIsUnchanged() {
        SystemRole disabledRole = role(8L, false);
        disabledRole.setStatus(RoleStatus.DISABLED);
        when(tenantMapper.lockByIdForAdminInvariant(1L)).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(disabledRole);

        TenantScope.run(1L, () -> service.disable(8L));

        verify(roleMapper, never()).updateById(any(SystemRole.class));
        verify(userRoleMapper, never()).selectUserIdsByRole(1L, 8L);
        verify(sessionRevocationService, never()).revokeAllUserSessions(1L, 10L);
    }

    @Test
    void shouldRejectMenuIdsOutsideEnabledGlobalMenuSet() {
        when(roleMapper.selectOne(any())).thenReturn(role(8L, false));
        when(menuMapper.countEnabledByIds(Set.of(101L, 999L))).thenReturn(1L);

        TenantScope.run(1L, () -> assertThatThrownBy(() -> service.assignMenus(8L, Set.of(101L, 999L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RoleErrorCode.INVALID_MENU_ASSIGNMENT));

        verify(roleMenuMapper, never()).deleteByRole(1L, 8L);
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

    private SystemRole role(long id, boolean builtIn) {
        SystemRole role = new SystemRole();
        role.setId(id);
        role.setTenantId(1L);
        role.setRoleCode("ANALYST");
        role.setRoleName("分析员");
        role.setStatus(RoleStatus.ENABLED);
        role.setBuiltIn(builtIn);
        role.setDeleted(false);
        return role;
    }
}
