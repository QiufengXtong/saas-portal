package com.xtong.saas.system.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.mybatis.AuditorProvider;
import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.role.entity.SystemUserRole;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.user.dto.CreateUserDTO;
import com.xtong.saas.system.user.dto.ResetPasswordDTO;
import com.xtong.saas.system.user.dto.UpdateUserDTO;
import com.xtong.saas.system.user.dto.UserQueryDTO;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.exception.UserErrorCode;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import com.xtong.saas.system.user.service.UserService;
import com.xtong.saas.system.user.vo.UserVO;
import com.xtong.saas.system.identity.IdentityNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 在受信租户上下文内维护用户、角色关系，并在事务提交后撤销受影响会话。 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private final SystemUserMapper userMapper;
    private final SystemRoleMapper roleMapper;
    private final SystemUserRoleMapper userRoleMapper;
    private final SystemTenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessionRevocationService;
    private final AuditorProvider auditorProvider;

    /** 创建用户服务并注入用户、角色、租户、会话及审计依赖。 */
    public UserServiceImpl(
            SystemUserMapper userMapper,
            SystemRoleMapper roleMapper,
            SystemUserRoleMapper userRoleMapper,
            SystemTenantMapper tenantMapper,
            PasswordEncoder passwordEncoder,
            SessionRevocationService sessionRevocationService,
            AuditorProvider auditorProvider) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.tenantMapper = tenantMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevocationService = sessionRevocationService;
        this.auditorProvider = auditorProvider;
    }

    /** 分页查询当前租户用户并组装角色信息。 */
    @Override
    public PageResult<UserVO> page(UserQueryDTO query) {
        long tenantId = TenantContextHolder.requireTenantId();
        Page<SystemUser> page = userMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()),
                Wrappers.<SystemUser>query().lambda()
                        .eq(SystemUser::getTenantId, tenantId)
                        .eq(SystemUser::getDeleted, false)
                        .like(query.username() != null && !query.username().isBlank(), SystemUser::getUsername, query.username())
                        .eq(query.status() != null, SystemUser::getStatus, query.status())
                        .orderByAsc(SystemUser::getId));
        return PageResult.from(page, user -> toView(tenantId, user));
    }

    /** 获取当前租户内指定用户的详情。 */
    @Override
    public UserVO get(long userId) {
        long tenantId = TenantContextHolder.requireTenantId();
        return toView(tenantId, requireUser(tenantId, userId));
    }

    /** 创建用户、校验角色并建立初始角色关系。 */
    @Override
    @Transactional
    public String create(CreateUserDTO command) {
        long tenantId = TenantContextHolder.requireTenantId();
        String username = IdentityNormalizer.requireManagement(command.username());
        if (userMapper.countByTenantAndUsernameIncludingDeleted(tenantId, username) > 0) {
            throw new BusinessException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (command.roleIds() != null && !command.roleIds().isEmpty()) {
            lockTenantForAdminInvariant(tenantId);
        }
        validateRoleIds(tenantId, command.roleIds());
        SystemUser user = new SystemUser();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setDisplayName(command.displayName());
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setEmail(command.email());
        user.setMobile(command.mobile());
        user.setStatus(UserStatus.ENABLED);
        user.setPasswordChangedAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
        replaceRoles(tenantId, user.getId(), command.roleIds());
        return user.getId().toString();
    }

    /** 更新用户资料并使该用户已有会话失效。 */
    @Override
    @Transactional
    public void update(long userId, UpdateUserDTO command) {
        long tenantId = TenantContextHolder.requireTenantId();
        lockTenantForAdminInvariant(tenantId);
        SystemUser user = requireUser(tenantId, userId);
        if (command.username() != null) {
            String username = IdentityNormalizer.requireManagement(command.username());
            if (!username.equals(user.getUsername())
                    && userMapper.countByTenantAndUsernameIncludingDeleted(tenantId, username) > 0) {
                throw new BusinessException(UserErrorCode.USERNAME_ALREADY_EXISTS);
            }
            user.setUsername(username);
        }
        user.setDisplayName(command.displayName());
        user.setEmail(command.email());
        user.setMobile(command.mobile());
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
        userMapper.incrementAuthVersion(tenantId, userId);
        revokeAfterCommit(tenantId, userId);
    }

    /** 启用指定用户并刷新其认证版本。 */
    @Override
    @Transactional
    public void enable(long userId) {
        long tenantId = TenantContextHolder.requireTenantId();
        changeStatus(tenantId, userId, UserStatus.ENABLED);
    }

    /** 禁用非当前用户，同时保护租户最后一个有效管理员。 */
    @Override
    @Transactional
    public void disable(long userId, long currentUserId) {
        if (userId == currentUserId) {
            throw new BusinessException(UserErrorCode.CANNOT_OPERATE_CURRENT_USER);
        }
        long tenantId = TenantContextHolder.requireTenantId();
        lockTenantForAdminInvariant(tenantId);
        SystemUser user = requireUser(tenantId, userId);
        assertNotLastEnabledTenantAdmin(tenantId, user);
        if (user.getStatus() != UserStatus.DISABLED) {
            user.setStatus(UserStatus.DISABLED);
            userMapper.updateById(user);
            userMapper.incrementAuthVersion(tenantId, userId);
            revokeAfterCommit(tenantId, userId);
        }
    }

    /** 重置用户密码并撤销已有会话。 */
    @Override
    @Transactional
    public void resetPassword(long userId, ResetPasswordDTO command) {
        long tenantId = TenantContextHolder.requireTenantId();
        lockTenantForAdminInvariant(tenantId);
        SystemUser user = requireUser(tenantId, userId);
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userMapper.updateById(user);
        userMapper.incrementAuthVersion(tenantId, userId);
        revokeAfterCommit(tenantId, userId);
    }

    /** 逻辑删除非当前用户及其角色关系，并撤销已有会话。 */
    @Override
    @Transactional
    public void delete(long userId, long currentUserId) {
        if (userId == currentUserId) {
            throw new BusinessException(UserErrorCode.CANNOT_OPERATE_CURRENT_USER);
        }
        long tenantId = TenantContextHolder.requireTenantId();
        lockTenantForAdminInvariant(tenantId);
        SystemUser user = requireUser(tenantId, userId);
        assertNotLastEnabledTenantAdmin(tenantId, user);
        userMapper.logicalDeleteWithAudit(tenantId, userId, currentAuditorId(), LocalDateTime.now());
        userRoleMapper.deleteByUser(tenantId, userId);
        revokeAfterCommit(tenantId, userId);
    }

    /** 替换用户角色，并保证租户至少保留一个有效管理员。 */
    @Override
    @Transactional
    public void assignRoles(long userId, Set<Long> roleIds) {
        long tenantId = TenantContextHolder.requireTenantId();
        lockTenantForAdminInvariant(tenantId);
        validateRoleIds(tenantId, roleIds);
        SystemUser user = requireUser(tenantId, userId);
        assertRoleReplacementPreservesTenantAdmin(tenantId, user, roleIds);
        Set<Long> currentRoleIds = Set.copyOf(userRoleMapper.selectRoleIdsByUserId(tenantId, userId));
        if (currentRoleIds.equals(roleIds)) {
            return;
        }
        replaceRoles(tenantId, userId, roleIds);
        userMapper.incrementAuthVersion(tenantId, userId);
        revokeAfterCommit(tenantId, userId);
    }

    /** 在指定租户作用域内加载并校验可登录用户。 */
    @Override
    public SystemUser requireEnabledForLogin(long tenantId, String username) {
        return TenantScope.call(tenantId, () -> {
            SystemUser user = userMapper.selectOne(Wrappers.<SystemUser>query().lambda()
                    .eq(SystemUser::getTenantId, tenantId)
                    .eq(SystemUser::getUsername, username)
                    .eq(SystemUser::getDeleted, false));
            if (user == null) {
                throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
            }
            if (user.getStatus() != UserStatus.ENABLED) {
                throw new BusinessException(UserErrorCode.USER_DISABLED);
            }
            return user;
        });
    }

    /** 加载并校验可继续使用会话的用户。 */
    @Override
    public SystemUser requireEnabledForSession(long tenantId, long userId) {
        SystemUser user = requireUser(tenantId, userId);
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException(UserErrorCode.USER_DISABLED);
        }
        return user;
    }

    /** 更新用户最近一次成功登录时间。 */
    @Override
    @Transactional
    public void recordLoginSuccess(long userId, LocalDateTime loginAt) {
        long tenantId = TenantContextHolder.requireTenantId();
        SystemUser user = requireUser(tenantId, userId);
        user.setLastLoginAt(loginAt);
        userMapper.updateById(user);
    }

    /** 修改用户状态、递增认证版本并安排会话撤销。 */
    private void changeStatus(long tenantId, long userId, UserStatus status) {
        lockTenantForAdminInvariant(tenantId);
        SystemUser user = requireUser(tenantId, userId);
        if (user.getStatus() != status) {
            user.setStatus(status);
            userMapper.updateById(user);
            userMapper.incrementAuthVersion(tenantId, userId);
            revokeAfterCommit(tenantId, userId);
        }
    }

    /** 校验角色 ID 集合完整且全部属于当前租户。 */
    private void validateRoleIds(long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.stream().anyMatch(roleId -> roleId == null || roleId <= 0)
                || (!roleIds.isEmpty() && roleMapper.countByTenantAndIds(tenantId, roleIds) != roleIds.size())) {
            throw new BusinessException(UserErrorCode.INVALID_ROLE_ASSIGNMENT);
        }
    }

    /** 物理替换用户角色关系并写入创建审计信息。 */
    private void replaceRoles(long tenantId, long userId, Set<Long> roleIds) {
        userRoleMapper.deleteByUser(tenantId, userId);
        if (!roleIds.isEmpty()) {
            long auditorId = currentAuditorId();
            LocalDateTime createdAt = LocalDateTime.now();
            List<SystemUserRole> relations = roleIds.stream().map(roleId -> {
                SystemUserRole relation = new SystemUserRole();
                relation.setId(IdWorker.getId());
                relation.setTenantId(tenantId);
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                relation.setCreatedBy(auditorId);
                relation.setCreatedAt(createdAt);
                return relation;
            }).toList();
            userRoleMapper.insertBatch(relations);
        }
    }

    /** 加载租户内未删除用户，不存在时抛出业务异常。 */
    private SystemUser requireUser(long tenantId, long userId) {
        SystemUser user = userMapper.selectOne(Wrappers.<SystemUser>query().lambda()
                .eq(SystemUser::getTenantId, tenantId)
                .eq(SystemUser::getId, userId)
                .eq(SystemUser::getDeleted, false));
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /** 将用户实体及其角色 ID 组装为用户视图。 */
    private UserVO toView(long tenantId, SystemUser user) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(tenantId, user.getId());
        return UserVO.from(user, roleIds);
    }

    /** 阻止停用或删除租户最后一个有效管理员。 */
    private void assertNotLastEnabledTenantAdmin(long tenantId, SystemUser user) {
        if (user.getStatus() == UserStatus.ENABLED
                && roleMapper.existsTenantAdminRole(tenantId, user.getId())
                && roleMapper.countEnabledTenantAdminUsers(tenantId) <= 1) {
            throw new BusinessException(UserErrorCode.LAST_TENANT_ADMIN);
        }
    }

    /** 阻止角色替换移除租户最后一个有效管理员资格。 */
    private void assertRoleReplacementPreservesTenantAdmin(long tenantId, SystemUser user, Set<Long> roleIds) {
        boolean keepsTenantAdminRole = !roleIds.isEmpty()
                && roleMapper.containsTenantAdminRole(tenantId, roleIds);
        if (user.getStatus() == UserStatus.ENABLED
                && roleMapper.existsTenantAdminRole(tenantId, user.getId())
                && !keepsTenantAdminRole
                && roleMapper.countEnabledTenantAdminUsers(tenantId) <= 1) {
            throw new BusinessException(UserErrorCode.LAST_TENANT_ADMIN);
        }
    }

    /** 锁定租户行以串行化管理员不变量检查。 */
    private void lockTenantForAdminInvariant(long tenantId) {
        tenantMapper.lockByIdForAdminInvariant(tenantId);
    }

    /** 在事务提交后撤销用户会话，无活动事务时立即撤销。 */
    private void revokeAfterCommit(long tenantId, long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 在数据库事务成功提交后执行会话撤销。 */
                @Override
                public void afterCommit() {
                    safelyRevoke(tenantId, userId);
                }
            });
            return;
        }
        safelyRevoke(tenantId, userId);
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
                .orElseThrow(() -> new IllegalStateException("User management requires an authenticated auditor"));
    }
}
