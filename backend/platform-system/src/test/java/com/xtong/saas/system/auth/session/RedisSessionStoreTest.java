package com.xtong.saas.system.auth.session;

import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.auth.config.SessionRevocationFallbackConfig;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.token.TokenHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redis 会话键、TTL、序列化、令牌轮换和多设备撤销协议。 */
@ExtendWith(MockitoExtension.class)
class RedisSessionStoreTest {

    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private ObjectMapper objectMapper;
    private RedisSessionStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        store = new RedisSessionStore(redisTemplate, objectMapper);
    }

    @Test
    void shouldStoreOnlyRefreshHashAndKeepMultipleDeviceSessionIds() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        String rawRefreshToken = "raw-refresh-token-that-must-never-reach-redis";
        String refreshTokenHash = new TokenHashService().hash(rawRefreshToken);
        AuthSession first = session("s1", refreshTokenHash);
        AuthSession second = session("s2", "second-refresh-hash");

        store.create(first, refreshTokenHash, TTL);
        store.create(second, "second-refresh-hash", TTL);

        ArgumentCaptor<String> serialized = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("saas:portal:auth:session:s1"), serialized.capture(), eq(TTL));
        assertThat(objectMapper.readValue(serialized.getValue(), AuthSession.class)).isEqualTo(first);
        verify(valueOperations).set("saas:portal:auth:refresh:" + refreshTokenHash, "s1", TTL);
        verify(setOperations).add(eq("saas:portal:auth:user-sessions:1:2"), eq(new String[]{"s1"}));
        verify(setOperations).add(eq("saas:portal:auth:user-sessions:1:2"), eq(new String[]{"s2"}));
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .expire("saas:portal:auth:user-sessions:1:2", TTL);
        assertThat(allStringArguments(redisTemplate, valueOperations, setOperations))
                .doesNotContain(rawRefreshToken);
    }

    @Test
    void shouldConsumeRefreshTokenOnlyOnceWithAtomicGetAndDelete() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String refreshTokenHash = "refresh-hash";
        when(valueOperations.getAndDelete("saas:portal:auth:refresh:" + refreshTokenHash))
                .thenReturn("s1")
                .thenReturn(null);

        assertThat(store.consumeRefreshToken(refreshTokenHash)).contains("s1");
        assertThat(store.consumeRefreshToken(refreshTokenHash)).isEmpty();

        verify(valueOperations, org.mockito.Mockito.times(2))
                .getAndDelete("saas:portal:auth:refresh:" + refreshTokenHash);
        verify(valueOperations, never()).get("saas:portal:auth:refresh:" + refreshTokenHash);
    }

    @Test
    void shouldFindSerializedSession() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AuthSession expected = session("s1", "refresh-hash");
        when(valueOperations.get("saas:portal:auth:session:s1"))
                .thenReturn(objectMapper.writeValueAsString(expected));

        Optional<AuthSession> actual = store.find("s1");

        assertThat(actual).contains(expected);
    }

    @Test
    void shouldReplaceRefreshHashAndRefreshAllRelatedTtls() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        AuthSession current = session("s1", "old-hash");

        store.replaceRefreshToken(current, "new-hash", TTL);

        verify(redisTemplate).delete("saas:portal:auth:refresh:old-hash");
        verify(valueOperations).set("saas:portal:auth:refresh:new-hash", "s1", TTL);
        ArgumentCaptor<String> serialized = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("saas:portal:auth:session:s1"), serialized.capture(), eq(TTL));
        assertThat(objectMapper.readValue(serialized.getValue(), AuthSession.class).refreshTokenHash())
                .isEqualTo("new-hash");
        verify(setOperations).add(eq("saas:portal:auth:user-sessions:1:2"), eq(new String[]{"s1"}));
        verify(redisTemplate).expire("saas:portal:auth:user-sessions:1:2", TTL);
    }

    @Test
    void shouldDeleteOneSessionWithoutDeletingOtherDeviceSet() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        AuthSession current = session("s1", "refresh-hash");
        when(valueOperations.get("saas:portal:auth:session:s1"))
                .thenReturn(objectMapper.writeValueAsString(current));
        when(setOperations.size("saas:portal:auth:user-sessions:1:2")).thenReturn(1L);

        store.delete("s1");

        verify(redisTemplate).delete("saas:portal:auth:session:s1");
        verify(redisTemplate).delete("saas:portal:auth:refresh:refresh-hash");
        verify(setOperations).remove("saas:portal:auth:user-sessions:1:2", "s1");
        verify(redisTemplate, never()).delete("saas:portal:auth:user-sessions:1:2");
    }

    @Test
    void shouldDeleteAllSessionsAndRefreshHashesForOneUser() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        AuthSession first = session("s1", "first-hash");
        AuthSession second = session("s2", "second-hash");
        when(setOperations.members("saas:portal:auth:user-sessions:1:2"))
                .thenReturn(Set.of("s1", "s2"));
        when(valueOperations.get("saas:portal:auth:session:s1"))
                .thenReturn(objectMapper.writeValueAsString(first));
        when(valueOperations.get("saas:portal:auth:session:s2"))
                .thenReturn(objectMapper.writeValueAsString(second));

        store.deleteAll(1L, 2L);

        verify(redisTemplate).delete("saas:portal:auth:session:s1");
        verify(redisTemplate).delete("saas:portal:auth:session:s2");
        verify(redisTemplate).delete("saas:portal:auth:refresh:first-hash");
        verify(redisTemplate).delete("saas:portal:auth:refresh:second-hash");
        verify(redisTemplate).delete("saas:portal:auth:user-sessions:1:2");
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

    private AuthSession session(String sessionId, String refreshTokenHash) {
        return new AuthSession(
                sessionId,
                1L,
                2L,
                "admin",
                "Administrator",
                Set.of("system:user:list"),
                refreshTokenHash);
    }

    private static Set<String> allStringArguments(Object... mocks) {
        return Arrays.stream(mocks)
                .flatMap(mockObject -> mockingDetails(mockObject).getInvocations().stream())
                .map(invocation -> invocation.getArguments())
                .flatMap(arguments -> Stream.of(arguments))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(java.util.stream.Collectors.toSet());
    }
}
