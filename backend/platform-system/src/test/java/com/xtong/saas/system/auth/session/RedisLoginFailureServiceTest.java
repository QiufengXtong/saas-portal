package com.xtong.saas.system.auth.session;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.exception.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redis Lua 登录失败计数、无歧义键、统计窗口和锁定期限。 */
@ExtendWith(MockitoExtension.class)
class RedisLoginFailureServiceTest {

    private static final String NORMALIZED_KEY = "saas:portal:auth:login-failure:7:default:5:admin";
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
    void shouldIncrementAndApplyWindowOrLockTtlInOneLuaCall() {
        doReturn(1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        service.recordFailure(" Default ", " ADMIN ");

        ArgumentCaptor<RedisScript<Long>> script = redisScriptCaptor();
        verify(redisTemplate).execute(
                script.capture(),
                eq(List.of(NORMALIZED_KEY)),
                eq("900000"), eq("3"), eq("1800000"));
        assertThat(script.getValue().getScriptAsString())
                .contains("INCR", "PTTL", "count >= tonumber(ARGV[2])", "PEXPIRE");
        verify(redisTemplate, never()).expire(any(String.class), any(Duration.class));
    }

    @Test
    void shouldRestoreFailureWindowWhenAnExistingCounterHasNoTtl() {
        doReturn(2L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        service.recordFailure("default", "admin");

        ArgumentCaptor<RedisScript<Long>> script = redisScriptCaptor();
        verify(redisTemplate).execute(
                script.capture(),
                eq(List.of(NORMALIZED_KEY)),
                eq("900000"), eq("3"), eq("1800000"));
        assertThat(script.getValue().getScriptAsString())
                .contains("ttl < 0", "PEXPIRE");
    }

    @Test
    void shouldPassThresholdAndLockDurationToTheAtomicScript() {
        doReturn(3L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        service.recordFailure("default", "admin");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(NORMALIZED_KEY)),
                eq("900000"), eq("3"), eq("1800000"));
    }

    @Test
    void shouldFailClosedWhenAtomicCounterDoesNotReturnAResult() {
        doReturn(null).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> service.recordFailure("default", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("count");
    }

    @Test
    void shouldRejectSubMillisecondFailureTtlsBeforeCallingLua() {
        AuthProperties subMillisecondWindow = mock(AuthProperties.class);
        when(subMillisecondWindow.loginFailureWindow()).thenReturn(Duration.ofNanos(1));
        RedisLoginFailureService windowService =
                new RedisLoginFailureService(redisTemplate, subMillisecondWindow);

        assertThatThrownBy(() -> windowService.recordFailure("default", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loginFailureWindow");

        AuthProperties subMillisecondLock = mock(AuthProperties.class);
        when(subMillisecondLock.loginFailureWindow()).thenReturn(FAILURE_WINDOW);
        when(subMillisecondLock.loginLockDuration()).thenReturn(Duration.ofNanos(1));
        RedisLoginFailureService lockService =
                new RedisLoginFailureService(redisTemplate, subMillisecondLock);

        assertThatThrownBy(() -> lockService.recordFailure("default", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loginLockDuration");
        verify(redisTemplate, never())
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void shouldUseUnambiguousLengthPrefixedNormalizedIdentityComponents() {
        doReturn(1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        service.recordFailure("a:b", "c");
        service.recordFailure("a", "b:c");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("saas:portal:auth:login-failure:3:a:b:1:c")),
                any(Object[].class));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("saas:portal:auth:login-failure:1:a:3:b:c")),
                any(Object[].class));
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<RedisScript<Long>> redisScriptCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(RedisScript.class);
    }
}
