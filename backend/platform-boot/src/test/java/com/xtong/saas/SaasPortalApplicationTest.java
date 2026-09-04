package com.xtong.saas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.data.redis.connect-timeout=100ms",
        "spring.data.redis.timeout=100ms"
})
class SaasPortalApplicationTest {

    @Test
    void contextLoads() {
    }
}
