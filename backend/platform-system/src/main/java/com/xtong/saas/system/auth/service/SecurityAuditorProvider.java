package com.xtong.saas.system.auth.service;

import com.xtong.saas.common.mybatis.AuditorProvider;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

/** 从当前 Spring Security 主体提供审计用户 ID，匿名与非系统主体返回空。 */
@Component
public class SecurityAuditorProvider implements AuditorProvider {

    @Override
    public OptionalLong currentAuditorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(principal.userId());
    }
}
