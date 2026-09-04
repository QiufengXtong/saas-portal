package com.xtong.saas.system.auth.session;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.exception.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redis 登录失败计数、规范化、统计窗口和锁定期限。 */
@ExtendWith(MockitoExtension.class)
class RedisLoginFailureServiceTest {

    private static final String NORMALIZED_KEY = "saas:portal:auth:login-failure:default:admin";
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLoginFailureService service;

    @BeforeEach
    void setUp() {
        service = new RedisLoginFailureService(redisTemplate, properties());
    }

    @Test
    void shouldNormalizeIdentityAndStartFailureWindowOnFirstFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(NORMALIZED_KEY)).thenReturn(1L);

        service.recordFailure(" Default ", " ADMIN ");

        verify(redisTemplate).expire(NORMALIZED_KEY, FAILURE_WINDOW);
    }

    @Test
    void shouldKeepOriginalWindowBeforeLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(NORMALIZED_KEY)).thenReturn(2L);

        service.recordFailure("default", "admin");

        verify(redisTemplate, never()).expire(NORMALIZED_KEY, FAILURE_WINDOW);
        verify(redisTemplate, never()).expire(NORMALIZED_KEY, LOCK_DURATION);
    }

    @Test
    void shouldApplyLockDurationWhenFailureLimitIsReached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(NORMALIZED_KEY)).thenReturn(3L);

        service.recordFailure("default", "admin");

        verify(redisTemplate).expire(NORMALIZED_KEY, LOCK_DURATION);
    }

    @Test
    void shouldRejectLockedLoginWithStableNonCredentialSpecificError() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(NORMALIZED_KEY)).thenReturn("3");

        assertThatThrownBy(() -> service.assertAllowed("DEFAULT", "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_LOCKED);
    }

    @Test
    void shouldAllowLoginBelowFailureLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(NORMALIZED_KEY)).thenReturn("2");

        assertThatCode(() -> service.assertAllowed("default", "admin")).doesNotThrowAnyException();
    }

    @Test
    void shouldClearNormalizedFailureStateAfterSuccessfulLogin() {
        service.clear(" Default ", " ADMIN ");

        verify(redisTemplate).delete(NORMALIZED_KEY);
    }

    private static AuthProperties properties() {
        return new AuthProperties(
                "0123456789abcdef0123456789abcdef",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                3,
                FAILURE_WINDOW,
                LOCK_DURATION);
    }
}
