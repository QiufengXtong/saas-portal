package com.xtong.saas.system.auth.service;

import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.service.impl.DatabaseSessionPrincipalValidator;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证数据库会话主体校验同时约束用户状态、逻辑删除查询和持久认证版本。 */
class DatabaseSessionPrincipalValidatorTest {

    private final SystemUserMapper userMapper = mock(SystemUserMapper.class);
    private final DatabaseSessionPrincipalValidator validator = new DatabaseSessionPrincipalValidator(userMapper);

    @Test
    void shouldAcceptOnlyEnabledUserWithMatchingAuthenticationVersion() {
        SystemUser user = new SystemUser();
        user.setId(2L);
        user.setTenantId(1L);
        user.setStatus(UserStatus.ENABLED);
        user.setAuthVersion(7L);
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThat(validator.isValid(session(7L))).isTrue();
        assertThat(validator.isValid(session(6L))).isFalse();
        user.setStatus(UserStatus.DISABLED);
        assertThat(validator.isValid(session(7L))).isFalse();
    }

    private static AuthSession session(long authVersion) {
        return new AuthSession("session-1", 1L, 2L, "admin", "Admin", Set.of(), authVersion, "hash");
    }
}
