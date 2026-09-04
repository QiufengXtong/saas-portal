package com.xtong.saas.system.auth.session;

import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.auth.config.SessionRevocationFallbackConfig;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.token.TokenHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证 Redis Lua 会话键、TTL、序列化、令牌原子轮换和多设备撤销协议。 */
@ExtendWith(MockitoExtension.class)
class RedisSessionStoreTest {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String TTL_MILLIS = "604800000";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisSessionStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        store = new RedisSessionStore(redisTemplate, objectMapper);
    }

    @Test
    void shouldCreateSessionRefreshMappingAndUserIndexInOneLuaCall() throws Exception {
        doReturn(1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        String rawRefreshToken = "raw-refresh-token-that-must-never-reach-redis";
        String refreshTokenHash = new TokenHashService().hash(rawRefreshToken);
        AuthSession first = session("s1", 1L, 2L, refreshTokenHash);

        assertThat(store.create(first, refreshTokenHash, TTL)).isTrue();

        ArgumentCaptor<String> serialized = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RedisScript<Long>> script = redisScriptCaptor();
        verify(redisTemplate).execute(
                script.capture(),
                eq(List.of(
                        "saas:portal:auth:session:s1",
                        "saas:portal:auth:refresh:" + refreshTokenHash,
                        "saas:portal:auth:user-sessions:1:2")),
                serialized.capture(), eq("s1"), eq(TTL_MILLIS));
        @SuppressWarnings("unchecked")
        Map<String, Object> stored = objectMapper.readValue(serialized.getValue(), Map.class);
        assertThat(stored)
                .containsEntry("tenantId", "1")
                .containsEntry("userId", "2")
                .containsEntry("refreshTokenHash", refreshTokenHash);
        assertThat(serialized.getValue()).doesNotContain(rawRefreshToken);
        assertThat(script.getValue().getScriptAsString())
                .contains("EXISTS", "SET", "SADD", "PEXPIRE");
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void shouldKeepMultipleDeviceIdsInTheSameAtomicUserIndex() {
        doReturn(1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThat(store.create(session("s1", 1L, 2L, "first-hash"), "first-hash", TTL)).isTrue();
        assertThat(store.create(session("s2", 1L, 2L, "second-hash"), "second-hash", TTL)).isTrue();

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of(
                        "saas:portal:auth:session:s1",
                        "saas:portal:auth:refresh:first-hash",
                        "saas:portal:auth:user-sessions:1:2")),
                any(), eq("s1"), eq(TTL_MILLIS));
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of(
                        "saas:portal:auth:session:s2",
                        "saas:portal:auth:refresh:second-hash",
                        "saas:portal:auth:user-sessions:1:2")),
                any(), eq("s2"), eq(TTL_MILLIS));
    }

    @Test
    void shouldRejectSessionIdCollisionWithoutOverwritingAnotherIdentity() {
        doReturn(1L, 0L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        boolean firstCreated = store.create(session("same-id", 1L, 2L, "first-hash"), "first-hash", TTL);
        boolean secondCreated = store.create(session("same-id", 9L, 8L, "second-hash"), "second-hash", TTL);

        assertThat(firstCreated).isTrue();
        assertThat(secondCreated).isFalse();
        ArgumentCaptor<RedisScript<Long>> scripts = redisScriptCaptor();
        verify(redisTemplate, times(2)).execute(
                scripts.capture(), anyList(), any(Object[].class));
        assertThat(scripts.getAllValues())
                .allSatisfy(script -> assertThat(script.getScriptAsString()).contains("EXISTS", "'NX'"));
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void shouldRejectSubMillisecondSessionTtlBeforeAnyRedisCommand() {
        assertThatThrownBy(() -> store.create(
                session("s1", 1L, 2L, "refresh-hash"),
                "refresh-hash",
                Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("millisecond");

        assertThatThrownBy(() -> store.rotateRefreshToken(
                "old-hash",
                "new-hash",
                Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("millisecond");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldFindStoredStringIdentitySession() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AuthSession expected = session("s1", 1L, 2L, "refresh-hash");
        when(valueOperations.get("saas:portal:auth:session:s1"))
                .thenReturn(storedSessionJson(expected));

        Optional<AuthSession> actual = store.find("s1");

        assertThat(actual).contains(expected);
    }

    @Test
    void shouldPreserveAnEmptyPermissionSetAcrossLuaSafeSerialization() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AuthSession expected = new AuthSession(
                "s1", 1L, 2L, "admin", "Administrator", Set.of(), "refresh-hash");
        when(valueOperations.get("saas:portal:auth:session:s1"))
                .thenReturn(storedSessionJson(expected));

        assertThat(store.find("s1")).contains(expected);
    }

    @Test
    void shouldRotateRefreshExactlyOnceAndRemoveOldHashInOneLuaCall() throws Exception {
        AuthSession rotated = session("s1", 1L, 2L, "new-hash");
        doReturn(storedSessionJson(rotated), (Object) null).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        Optional<AuthSession> first = store.rotateRefreshToken("old-hash", "new-hash", TTL);
        Optional<AuthSession> repeated = store.rotateRefreshToken("old-hash", "another-hash", TTL);

        assertThat(first).contains(rotated);
        assertThat(repeated).isEmpty();
        ArgumentCaptor<RedisScript<String>> scripts = redisScriptCaptor();
        verify(redisTemplate).execute(
                scripts.capture(),
                eq(List.of(
                        "saas:portal:auth:refresh:old-hash",
                        "saas:portal:auth:refresh:new-hash")),
                eq("saas:portal:auth:session:"),
                eq("saas:portal:auth:user-sessions:"),
                eq("old-hash"), eq("new-hash"), eq(TTL_MILLIS));
        assertThat(scripts.getValue().getScriptAsString())
                .contains("GET", "EXISTS", "redis.call('DEL', KEYS[1])", "SADD", "PEXPIRE");
        verify(redisTemplate, never()).delete("saas:portal:auth:refresh:old-hash");
    }

    @Test
    void shouldNotReviveARevokedSessionWhenRotationRunsAfterDelete() {
        doReturn(1L, (Object) null).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        store.delete("s1");
        Optional<AuthSession> rotated = store.rotateRefreshToken("old-hash", "new-hash", TTL);

        assertThat(rotated).isEmpty();
        InOrder order = inOrder(redisTemplate);
        order.verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("saas:portal:auth:session:s1")),
                eq("saas:portal:auth:refresh:"), eq("saas:portal:auth:user-sessions:"));
        order.verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        "saas:portal:auth:refresh:old-hash",
                        "saas:portal:auth:refresh:new-hash")),
                any(Object[].class));
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void shouldDeleteOneSessionAndItsRefreshAndIndexAtomically() {
        doReturn(1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        store.delete("s1");

        ArgumentCaptor<RedisScript<Long>> script = redisScriptCaptor();
        verify(redisTemplate).execute(
                script.capture(),
                eq(List.of("saas:portal:auth:session:s1")),
                eq("saas:portal:auth:refresh:"), eq("saas:portal:auth:user-sessions:"));
        assertThat(script.getValue().getScriptAsString())
                .contains("GET", "DEL", "SREM", "SCARD");
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void shouldDeleteAllUserSessionsAndRefreshMappingsAtomically() {
        doReturn(2L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        store.deleteAll(1L, 2L);

        ArgumentCaptor<RedisScript<Long>> script = redisScriptCaptor();
        verify(redisTemplate).execute(
                script.capture(),
                eq(List.of("saas:portal:auth:user-sessions:1:2")),
                eq("saas:portal:auth:session:"), eq("saas:portal:auth:refresh:"));
        assertThat(script.getValue().getScriptAsString())
                .contains("SMEMBERS", "GET", "DEL");
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void shouldUseServerGeneratedHighEntropyUrlSafeSessionIds() {
        SessionIdGenerator generator = new SessionIdGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(first).doesNotContain("=", "+", "/");
    }

    @Test
    void shouldExposeRedisStoreAsTheSessionRevocationServiceInsteadOfFallback() {
        StringRedisTemplate contextRedis = mock(StringRedisTemplate.class);
        ObjectMapper contextMapper = new ObjectMapper();

        new ApplicationContextRunner()
                .withBean(StringRedisTemplate.class, () -> contextRedis)
                .withBean(ObjectMapper.class, () -> contextMapper)
                .withBean(RedisSessionStore.class)
                .withUserConfiguration(SessionRevocationFallbackConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionRevocationService.class);
                    assertThat(context.getBean(SessionRevocationService.class))
                            .isInstanceOf(RedisSessionStore.class);
                });
    }

    private AuthSession session(String sessionId, long tenantId, long userId, String refreshTokenHash) {
        return new AuthSession(
                sessionId,
                tenantId,
                userId,
                "admin",
                "Administrator",
                Set.of("system:user:list"),
                refreshTokenHash);
    }

    private String storedSessionJson(AuthSession session) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "sessionId", session.sessionId(),
                "tenantId", Long.toString(session.tenantId()),
                "userId", Long.toString(session.userId()),
                "username", session.username(),
                "displayName", session.displayName(),
                "permissions", session.permissions().stream().collect(Collectors.toMap(
                        Function.identity(), ignored -> Boolean.TRUE)),
                "refreshTokenHash", session.refreshTokenHash()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<RedisScript<T>> redisScriptCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(RedisScript.class);
    }
}
