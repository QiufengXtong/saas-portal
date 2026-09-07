package com.xtong.saas.system.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.mybatis.AuditorProvider;
import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.auth.api.SessionRevocationService;
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

    @Override
    public UserVO get(long userId) {
        long tenantId = TenantContextHolder.requireTenantId();
        return toView(tenantId, requireUser(tenantId, userId));
    }

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

    @Override
    @Transactional
    public void enable(long userId) {
        long tenantId = TenantContextHolder.requireTenantId();
        changeStatus(tenantId, userId, UserStatus.ENABLED);
    }

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

    @Override
    public SystemUser requireEnabledForSession(long tenantId, long userId) {
        SystemUser user = requireUser(tenantId, userId);
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException(UserErrorCode.USER_DISABLED);
        }
        return user;
    }

    @Override
    @Transactional
    public void recordLoginSuccess(long userId, LocalDateTime loginAt) {
        long tenantId = TenantContextHolder.requireTenantId();
        SystemUser user = requireUser(tenantId, userId);
        user.setLastLoginAt(loginAt);
        userMapper.updateById(user);
    }

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

    private void validateRoleIds(long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.stream().anyMatch(roleId -> roleId == null || roleId <= 0)
                || (!roleIds.isEmpty() && roleMapper.countByTenantAndIds(tenantId, roleIds) != roleIds.size())) {
            throw new BusinessException(UserErrorCode.INVALID_ROLE_ASSIGNMENT);
        }
    }

    private void replaceRoles(long tenantId, long userId, Set<Long> roleIds) {
        userRoleMapper.deleteByUser(tenantId, userId);
        if (!roleIds.isEmpty()) {
            userRoleMapper.insertBatch(tenantId, userId, roleIds, currentAuditorId(), LocalDateTime.now());
        }
    }

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

    private UserVO toView(long tenantId, SystemUser user) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(tenantId, user.getId());
        return UserVO.from(user, roleIds);
    }

    private void assertNotLastEnabledTenantAdmin(long tenantId, SystemUser user) {
        if (user.getStatus() == UserStatus.ENABLED
                && roleMapper.existsTenantAdminRole(tenantId, user.getId())
                && roleMapper.countEnabledTenantAdminUsers(tenantId) <= 1) {
            throw new BusinessException(UserErrorCode.LAST_TENANT_ADMIN);
        }
    }

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

    private void lockTenantForAdminInvariant(long tenantId) {
        tenantMapper.lockByIdForAdminInvariant(tenantId);
    }

    private void revokeAfterCommit(long tenantId, long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safelyRevoke(tenantId, userId);
                }
            });
            return;
        }
        safelyRevoke(tenantId, userId);
    }

    private void safelyRevoke(long tenantId, long userId) {
        try {
            sessionRevocationService.revokeAllUserSessions(tenantId, userId);
        } catch (RuntimeException exception) {
            LOGGER.warn("会话清理失败，持久认证版本仍会阻止旧会话: tenantId={}, userId={}",
                    tenantId, userId, exception);
        }
    }

    private long currentAuditorId() {
        return auditorProvider.currentAuditorId()
                .orElseThrow(() -> new IllegalStateException("User management requires an authenticated auditor"));
    }
}
