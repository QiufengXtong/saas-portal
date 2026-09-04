package com.xtong.saas.system.role.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.role.dto.CreateRoleDTO;
import com.xtong.saas.system.role.dto.RoleQueryDTO;
import com.xtong.saas.system.role.dto.UpdateRoleDTO;
import com.xtong.saas.system.role.entity.SystemRole;
import com.xtong.saas.system.role.enums.RoleStatus;
import com.xtong.saas.system.role.exception.RoleErrorCode;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.role.service.RoleService;
import com.xtong.saas.system.role.vo.RoleVO;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 在受信租户上下文内维护角色及菜单关系，并在事务提交后撤销受影响用户会话。 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private final SystemRoleMapper roleMapper;
    private final SystemUserRoleMapper userRoleMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final SystemMenuMapper menuMapper;
    private final SessionRevocationService sessionRevocationService;

    public RoleServiceImpl(
            SystemRoleMapper roleMapper,
            SystemUserRoleMapper userRoleMapper,
            SystemRoleMenuMapper roleMenuMapper,
            SystemMenuMapper menuMapper,
            SessionRevocationService sessionRevocationService) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Override
    public PageResult<RoleVO> page(RoleQueryDTO query) {
        long tenantId = TenantContextHolder.requireTenantId();
        Page<SystemRole> page = roleMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()),
                Wrappers.<SystemRole>query()
                        .eq("tenant_id", tenantId)
                        .eq("deleted", false)
                        .like(query.roleCode() != null && !query.roleCode().isBlank(), "role_code", query.roleCode())
                        .like(query.roleName() != null && !query.roleName().isBlank(), "role_name", query.roleName())
                        .eq(query.status() != null, "status", query.status())
                        .orderByAsc("id"));
        return PageResult.from(page, RoleVO::from);
    }

    @Override
    public RoleVO get(long roleId) {
        long tenantId = TenantContextHolder.requireTenantId();
        return RoleVO.from(requireRole(tenantId, roleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(CreateRoleDTO command) {
        long tenantId = TenantContextHolder.requireTenantId();
        if (roleMapper.countByTenantAndCodeIncludingDeleted(tenantId, command.roleCode()) > 0) {
            throw new BusinessException(RoleErrorCode.ROLE_CODE_ALREADY_EXISTS);
        }
        SystemRole role = new SystemRole();
        role.setTenantId(tenantId);
        role.setRoleCode(command.roleCode());
        role.setRoleName(command.roleName());
        role.setStatus(RoleStatus.ENABLED);
        role.setBuiltIn(false);
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(RoleErrorCode.ROLE_CODE_ALREADY_EXISTS);
        }
        return role.getId().toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(long roleId, UpdateRoleDTO command) {
        long tenantId = TenantContextHolder.requireTenantId();
        SystemRole role = requireRole(tenantId, roleId);
        role.setRoleName(command.roleName());
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(long roleId) {
        changeStatus(TenantContextHolder.requireTenantId(), roleId, RoleStatus.ENABLED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(long roleId) {
        long tenantId = TenantContextHolder.requireTenantId();
        SystemRole role = requireRole(tenantId, roleId);
        assertNotProtected(role);
        if (role.getStatus() != RoleStatus.DISABLED) {
            role.setStatus(RoleStatus.DISABLED);
            roleMapper.updateById(role);
            revokeAffectedUsersAfterCommit(tenantId, roleId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long roleId) {
        long tenantId = TenantContextHolder.requireTenantId();
        SystemRole role = requireRole(tenantId, roleId);
        assertNotProtected(role);
        if (userRoleMapper.countByRole(tenantId, roleId) > 0) {
            throw new BusinessException(RoleErrorCode.ROLE_IN_USE);
        }
        roleMenuMapper.deleteByRole(tenantId, roleId);
        roleMapper.deleteById(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(long roleId, Set<Long> menuIds) {
        long tenantId = TenantContextHolder.requireTenantId();
        SystemRole role = requireRole(tenantId, roleId);
        assertNotProtected(role);
        validateMenuIds(menuIds);
        List<Long> userIds = userRoleMapper.selectUserIdsByRole(tenantId, roleId);
        roleMenuMapper.deleteByRole(tenantId, roleId);
        if (!menuIds.isEmpty()) {
            roleMenuMapper.insertBatch(tenantId, roleId, menuIds);
        }
        registerRevocationsAfterCommit(tenantId, userIds);
    }

    private void changeStatus(long tenantId, long roleId, RoleStatus status) {
        SystemRole role = requireRole(tenantId, roleId);
        if (role.getStatus() != status) {
            role.setStatus(status);
            roleMapper.updateById(role);
            revokeAffectedUsersAfterCommit(tenantId, roleId);
        }
    }

    private SystemRole requireRole(long tenantId, long roleId) {
        SystemRole role = roleMapper.selectOne(Wrappers.<SystemRole>query()
                .eq("tenant_id", tenantId)
                .eq("id", roleId)
                .eq("deleted", false));
        if (role == null || role.getTenantId() == null || role.getTenantId() != tenantId) {
            throw new BusinessException(RoleErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private void assertNotProtected(SystemRole role) {
        if (Boolean.TRUE.equals(role.getBuiltIn()) || TENANT_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
            throw new BusinessException(RoleErrorCode.BUILT_IN_ROLE_PROTECTED);
        }
    }

    private void validateMenuIds(Set<Long> menuIds) {
        if (menuIds == null || menuIds.stream().anyMatch(menuId -> menuId == null || menuId <= 0)
                || (!menuIds.isEmpty() && menuMapper.countEnabledByIds(menuIds) != menuIds.size())) {
            throw new BusinessException(RoleErrorCode.INVALID_MENU_ASSIGNMENT);
        }
    }

    private void revokeAffectedUsersAfterCommit(long tenantId, long roleId) {
        registerRevocationsAfterCommit(tenantId, userRoleMapper.selectUserIdsByRole(tenantId, roleId));
    }

    private void registerRevocationsAfterCommit(long tenantId, List<Long> userIds) {
        Set<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    distinctUserIds.forEach(userId -> sessionRevocationService.revokeAllUserSessions(tenantId, userId));
                }
            });
            return;
        }
        distinctUserIds.forEach(userId -> sessionRevocationService.revokeAllUserSessions(tenantId, userId));
    }
}
