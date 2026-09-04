package com.xtong.saas.system.auth.session;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.exception.AuthErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** 使用 Redis Lua 原子维护无歧义身份键的登录失败窗口及锁定期限。 */
@Service
public class RedisLoginFailureService implements LoginFailureService {

    private static final String LOGIN_FAILURE_KEY_PREFIX = "saas:portal:auth:login-failure:";

    /** 原子递增计数，并为首次或意外缺失 TTL 的计数恢复窗口、为阈值计数设置锁定期。 */
    private static final DefaultRedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if count == 1 or ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if count >= tonumber(ARGV[2]) then
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public RedisLoginFailureService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void assertAllowed(String tenantCode, String username) {
        String failures = redisTemplate.opsForValue().get(key(tenantCode, username));
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
        String failureWindowMillis = ttlMillis(
                properties.loginFailureWindow(), "loginFailureWindow");
        String lockDurationMillis = ttlMillis(
                properties.loginLockDuration(), "loginLockDuration");
        Long failureCount = redisTemplate.execute(
                RECORD_FAILURE_SCRIPT,
                List.of(key(tenantCode, username)),
                failureWindowMillis,
                Integer.toString(properties.loginFailureLimit()),
                lockDurationMillis);
        if (failureCount == null) {
            throw new IllegalStateException("Login failure count could not be updated");
        }
    }

    @Override
    public void clear(String tenantCode, String username) {
        redisTemplate.delete(key(tenantCode, username));
    }

    private static String key(String tenantCode, String username) {
        String normalizedTenant = normalize(tenantCode);
        String normalizedUsername = normalize(username);
        return LOGIN_FAILURE_KEY_PREFIX
                + component(normalizedTenant)
                + ":"
                + component(normalizedUsername);
    }

    private static String component(String value) {
        return value.length() + ":" + value;
    }

    private static String ttlMillis(Duration ttl, String propertyName) {
        if (ttl == null) {
            throw new IllegalArgumentException(propertyName + " must be at least 1 millisecond");
        }
        long milliseconds;
        try {
            milliseconds = ttl.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(propertyName + " must fit in milliseconds", exception);
        }
        if (milliseconds < 1L) {
            throw new IllegalArgumentException(propertyName + " must be at least 1 millisecond");
        }
        return Long.toString(milliseconds);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
