package com.xtong.saas.system.role.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.mybatis.AuditorProvider;
import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.role.dto.CreateRoleDTO;
import com.xtong.saas.system.role.dto.RoleQueryDTO;
import com.xtong.saas.system.role.dto.UpdateRoleDTO;
import com.xtong.saas.system.role.entity.SystemRole;
import com.xtong.saas.system.role.entity.SystemRoleMenu;
import com.xtong.saas.system.role.enums.RoleStatus;
import com.xtong.saas.system.role.exception.RoleErrorCode;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.role.service.RoleService;
import com.xtong.saas.system.role.vo.RoleVO;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

/** 在受信租户上下文内维护角色及菜单关系，并在事务提交后撤销受影响用户会话。 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleServiceImpl.class);

    private static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private final SystemRoleMapper roleMapper;
    private final SystemUserRoleMapper userRoleMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final SystemMenuMapper menuMapper;
    private final SystemTenantMapper tenantMapper;
    private final SystemUserMapper userMapper;
    private final SessionRevocationService sessionRevocationService;
    private final AuditorProvider auditorProvider;

    /** 创建角色服务并注入角色、菜单、用户、会话及审计依赖。 */
    public RoleServiceImpl(
            SystemRoleMapper roleMapper,
            SystemUserRoleMapper userRoleMapper,
            SystemRoleMenuMapper roleMenuMapper,
            SystemMenuMapper menuMapper,
            SystemTenantMapper tenantMapper,
            SystemUserMapper userMapper,
            SessionRevocationService sessionRevocationService,
            AuditorProvider auditorProvider) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.sessionRevocationService = sessionRevocationService;
        this.auditorProvider = auditorProvider;
    }

    /** 分页查询当前租户角色并组装菜单授权信息。 */
    @Override
    public PageResult<RoleVO> page(RoleQueryDTO query) {
        long tenantId = TenantContextHolder.requireTenantId();
        Page<SystemRole> page = roleMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()),
                Wrappers.<SystemRole>query().lambda()
                        .eq(SystemRole::getTenantId, tenantId)
                        .eq(SystemRole::getDeleted, false)
                        .like(query.roleCode() != null && !query.roleCode().isBlank(), SystemRole::getRoleCode, query.roleCode())
                        .like(query.roleName() != null && !query.roleName().isBlank(), SystemRole::getRoleName, query.roleName())
                        .eq(query.status() != null, SystemRole::getStatus, query.status())
                        .orderByAsc(SystemRole::getId));
        return PageResult.from(page, RoleVO::from);
    }

    /** 获取当前租户内指定角色的详情。 */
    @Override
    public RoleVO get(long roleId) {
        long tenantId = TenantContextHolder.requireTenantId();
        return RoleVO.from(requireRole(tenantId, roleId));
    }

    /** 获取当前租户指定角色已授权的菜单 ID 列表。 */
    @Override
    public List<String> getMenuIds(long roleId) {
        long tenantId = TenantContextHolder.requireTenantId();
        requireRole(tenantId, roleId);
        return roleMenuMapper.selectMenuIdsByRole(tenantId, roleId).stream()
                .map(String::valueOf)
                .toList();
    }

    /** 创建租户角色，并校验角色编码唯一性。 */
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

    /** 更新租户角色的名称与说明。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(long roleId, UpdateRoleDTO command) {
        long tenantId = TenantContextHolder.requireTenantId();
        SystemRole role = requireRole(tenantId, roleId);
        role.setRoleName(command.roleName());
        roleMapper.updateById(role);
    }

    /** 启用指定租户角色。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(long roleId) {
        changeStatus(TenantContextHolder.requireTenantId(), roleId, RoleStatus.ENABLED, false);
    }

    /** 禁用指定角色，并保护内置角色及管理员不变量。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(long roleId) {
        changeStatus(TenantContextHolder.requireTenantId(), roleId, RoleStatus.DISABLED, true);
    }

    /** 删除未被用户引用的非内置角色。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long roleId) {
        long tenantId = TenantContextHolder.requireTenantId();
        lockTenantForAdminInvariant(tenantId);
        SystemRole role = requireRole(tenantId, roleId);
        assertNotProtected(role);
        if (userRoleMapper.countByRole(tenantId, roleId) > 0) {
            throw new BusinessException(RoleErrorCode.ROLE_IN_USE);
        }
        roleMenuMapper.deleteByRole(tenantId, roleId);
        roleMapper.logicalDeleteWithAudit(tenantId, roleId, currentAuditorId(), LocalDateTime.now());
    }

    /** 替换角色菜单授权，并使受影响用户会话失效。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(long roleId, Set<Long> menuIds) {
        long tenantId = TenantContextHolder.requireTenantId();
        lockTenantForAdminInvariant(tenantId);
        SystemRole role = requireRole(tenantId, roleId);
        assertNotProtected(role);
        validateMenuIds(menuIds);
        Set<Long> existingMenuIds = Set.copyOf(roleMenuMapper.selectMenuIdsByRole(tenantId, roleId));
        if (existingMenuIds.equals(menuIds)) {
            return;
        }
        List<Long> userIds = userRoleMapper.selectUserIdsByRole(tenantId, roleId);
        roleMenuMapper.deleteByRole(tenantId, roleId);
        if (!menuIds.isEmpty()) {
            long auditorId = currentAuditorId();
            LocalDateTime createdAt = LocalDateTime.now();
            List<SystemRoleMenu> relations = menuIds.stream().map(menuId -> {
                SystemRoleMenu relation = new SystemRoleMenu();
                relation.setId(IdWorker.getId());
                relation.setTenantId(tenantId);
                relation.setRoleId(roleId);
                relation.setMenuId(menuId);
                relation.setCreatedBy(auditorId);
                relation.setCreatedAt(createdAt);
                return relation;
            }).toList();
            roleMenuMapper.insertBatch(relations);
        }
        incrementAffectedAuthVersions(tenantId, userIds);
        registerRevocationsAfterCommit(tenantId, userIds);
    }

    /** 修改角色状态，并按需保护内置角色及撤销关联用户会话。 */
    private void changeStatus(long tenantId, long roleId, RoleStatus status, boolean protectBuiltInRole) {
        lockTenantForAdminInvariant(tenantId);
        SystemRole role = requireRole(tenantId, roleId);
        if (protectBuiltInRole) {
            assertNotProtected(role);
        }
        if (role.getStatus() != status) {
            List<Long> userIds = userRoleMapper.selectUserIdsByRole(tenantId, roleId);
            role.setStatus(status);
            roleMapper.updateById(role);
            incrementAffectedAuthVersions(tenantId, userIds);
            registerRevocationsAfterCommit(tenantId, userIds);
        }
    }

    /** 加载租户内未删除角色，不存在时抛出业务异常。 */
    private SystemRole requireRole(long tenantId, long roleId) {
        SystemRole role = roleMapper.selectOne(Wrappers.<SystemRole>query().lambda()
                .eq(SystemRole::getTenantId, tenantId)
                .eq(SystemRole::getId, roleId)
                .eq(SystemRole::getDeleted, false));
        if (role == null || role.getTenantId() == null || role.getTenantId() != tenantId) {
            throw new BusinessException(RoleErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    /** 阻止修改受保护的内置角色。 */
    private void assertNotProtected(SystemRole role) {
        if (Boolean.TRUE.equals(role.getBuiltIn()) || TENANT_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
            throw new BusinessException(RoleErrorCode.BUILT_IN_ROLE_PROTECTED);
        }
    }

    /** 校验菜单 ID 集合完整且全部指向有效菜单资源。 */
    private void validateMenuIds(Set<Long> menuIds) {
        if (menuIds == null || menuIds.stream().anyMatch(menuId -> menuId == null || menuId <= 0)
                || (!menuIds.isEmpty() && menuMapper.countEnabledByIds(menuIds) != menuIds.size())) {
            throw new BusinessException(RoleErrorCode.INVALID_MENU_ASSIGNMENT);
        }
    }

    /** 锁定租户行以串行化管理员不变量检查。 */
    private void lockTenantForAdminInvariant(long tenantId) {
        tenantMapper.lockByIdForAdminInvariant(tenantId);
    }

    /** 在事务提交后撤销所有受角色变更影响的用户会话。 */
    private void registerRevocationsAfterCommit(long tenantId, List<Long> userIds) {
        Set<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 在数据库事务成功提交后批量执行会话撤销。 */
                @Override
                public void afterCommit() {
                    distinctUserIds.forEach(userId -> safelyRevoke(tenantId, userId));
                }
            });
            return;
        }
        distinctUserIds.forEach(userId -> safelyRevoke(tenantId, userId));
    }

    /** 批量递增受角色变更影响用户的认证版本。 */
    private void incrementAffectedAuthVersions(long tenantId, List<Long> userIds) {
        List<Long> distinctUserIds = userIds.stream().distinct().toList();
        if (!distinctUserIds.isEmpty()) {
            userMapper.incrementAuthVersions(tenantId, distinctUserIds);
        }
    }

    /** 尽力撤销用户会话，并在存储异常时保留持久认证版本兜底。 */
    private void safelyRevoke(long tenantId, long userId) {
        try {
            sessionRevocationService.revokeAllUserSessions(tenantId, userId);
        } catch (RuntimeException exception) {
            LOGGER.warn("会话清理失败，持久认证版本仍会阻止旧会话: tenantId={}, userId={}",
                    tenantId, userId, exception);
        }
    }

    /** 获取当前审计用户 ID，缺少认证主体时拒绝管理操作。 */
    private long currentAuditorId() {
        return auditorProvider.currentAuditorId()
                .orElseThrow(() -> new IllegalStateException("Role management requires an authenticated auditor"));
    }
}
