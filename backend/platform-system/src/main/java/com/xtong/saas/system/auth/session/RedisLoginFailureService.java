package com.xtong.saas.system.auth.session;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.exception.AuthErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** 使用规范化租户编码和用户名维护 Redis 登录失败窗口及锁定期限。 */
@Service
public class RedisLoginFailureService implements LoginFailureService {

    private static final String LOGIN_FAILURE_KEY_PREFIX = "saas:portal:auth:login-failure:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public RedisLoginFailureService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void assertAllowed(String tenantCode, String username) {
        String failures = valueOperations().get(key(tenantCode, username));
        if (failures == null) {
            return;
        }
        try {
            if (Long.parseLong(failures) >= properties.loginFailureLimit()) {
                throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
    }

    @Override
    public void recordFailure(String tenantCode, String username) {
        String key = key(tenantCode, username);
        Long failureCount = valueOperations().increment(key);
        if (failureCount == null) {
            throw new IllegalStateException("Login failure count could not be updated");
        }
        if (failureCount >= properties.loginFailureLimit()) {
            redisTemplate.expire(key, properties.loginLockDuration());
        } else if (failureCount == 1L) {
            redisTemplate.expire(key, properties.loginFailureWindow());
        }
    }

    @Override
    public void clear(String tenantCode, String username) {
        redisTemplate.delete(key(tenantCode, username));
    }

    private ValueOperations<String, String> valueOperations() {
        return redisTemplate.opsForValue();
    }

    private static String key(String tenantCode, String username) {
        return LOGIN_FAILURE_KEY_PREFIX + normalize(tenantCode) + ":" + normalize(username);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
