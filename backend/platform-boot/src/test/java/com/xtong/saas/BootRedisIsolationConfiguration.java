package com.xtong.saas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;

/** 为 Boot 集成测试提供完全隔离的 Mockito Redis Bean，禁止测试访问本机真实 Redis。 */
@TestConfiguration(proxyBeanMethods = false)
class BootRedisIsolationConfiguration {

    @Bean
    RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate() {
        return mock(StringRedisTemplate.class);
    }
}
