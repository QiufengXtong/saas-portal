package com.xtong.saas.system.auth.config;

import com.xtong.saas.system.auth.api.SessionRevocationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 在会话存储尚未装配的阶段提供可被 Redis 实现安全替换的撤销端口占位 Bean。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(SessionRevocationService.class)
public class SessionRevocationFallbackConfig {

    /** 返回无会话存储时的空实现；实际 Redis 存储存在时本 Bean 不会注册。 */
    @Bean
    public SessionRevocationService sessionRevocationService() {
        return new SessionRevocationService() {
            @Override
            public void revokeSession(String sessionId) {
            }

            @Override
            public void revokeAllUserSessions(long tenantId, long userId) {
            }
        };
    }
}
