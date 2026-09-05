package com.xtong.saas.system.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.service.SessionPrincipalValidator;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.springframework.stereotype.Service;

/** 从当前租户数据库校验用户启用、未删除及认证版本，作为 Redis 清理失败时的安全兜底。 */
@Service
public class DatabaseSessionPrincipalValidator implements SessionPrincipalValidator {

    private final SystemUserMapper userMapper;

    public DatabaseSessionPrincipalValidator(SystemUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean isValid(AuthSession session) {
        SystemUser user = userMapper.selectOne(Wrappers.<SystemUser>query()
                .eq("tenant_id", session.tenantId())
                .eq("id", session.userId())
                .eq("deleted", false));
        long version = user == null || user.getAuthVersion() == null ? -1L : user.getAuthVersion();
        return user != null && user.getStatus() == UserStatus.ENABLED && version == session.authVersion();
    }
}
