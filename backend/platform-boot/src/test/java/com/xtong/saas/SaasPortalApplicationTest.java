package com.xtong.saas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 验证应用在隔离基础设施和测试专用认证配置下能够装配完整上下文。 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.data.redis.connect-timeout=100ms",
        "spring.data.redis.timeout=100ms",
        "saas.auth.jwt-secret=test-only-secret-with-at-least-32-bytes",
        "saas.auth.access-token-ttl=15m",
        "saas.auth.refresh-token-ttl=7d",
        "saas.auth.login-failure-limit=5",
        "saas.auth.login-failure-window=15m",
        "saas.auth.login-lock-duration=15m"
})
class SaasPortalApplicationTest {

    @Test
    void contextLoads() {
    }
}
