package com.xtong.saas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 验证应用能够使用隔离的 test 配置装配完整 Spring 上下文。 */
@SpringBootTest
@ActiveProfiles("test")
class SaasPortalApplicationTest {

    @Test
    void contextLoads() {
    }
}
