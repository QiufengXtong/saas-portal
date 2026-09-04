package com.xtong.saas.system.auth.session;

import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.auth.model.AuthSession;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/** 以命名空间化 Redis Key 保存多设备会话，并提供原子 Refresh Token 单次消费。 */
@Service
@Primary
public class RedisSessionStore implements SessionStore, SessionRevocationService {

    private static final String SESSION_KEY_PREFIX = "saas:portal:auth:session:";
    private static final String REFRESH_KEY_PREFIX = "saas:portal:auth:refresh:";
    private static final String USER_SESSIONS_KEY_PREFIX = "saas:portal:auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void create(AuthSession session, String refreshTokenHash, Duration ttl) {
        AuthSession storedSession = withRefreshTokenHash(session, refreshTokenHash);
        save(storedSession, ttl);
    }

    @Override
    public Optional<AuthSession> find(String sessionId) {
        String serialized = valueOperations().get(sessionKey(sessionId));
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(serialized, AuthSession.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored authentication session cannot be deserialized", exception);
        }
    }

    @Override
    public Optional<String> consumeRefreshToken(String refreshTokenHash) {
        return Optional.ofNullable(valueOperations().getAndDelete(refreshKey(refreshTokenHash)));
    }

    @Override
    public void replaceRefreshToken(AuthSession session, String newRefreshTokenHash, Duration ttl) {
        redisTemplate.delete(refreshKey(session.refreshTokenHash()));
        save(withRefreshTokenHash(session, newRefreshTokenHash), ttl);
    }

    @Override
    public void delete(String sessionId) {
        Optional<AuthSession> storedSession = find(sessionId);
        redisTemplate.delete(sessionKey(sessionId));
        storedSession.ifPresent(session -> {
            redisTemplate.delete(refreshKey(session.refreshTokenHash()));
            String userSessionsKey = userSessionsKey(session.tenantId(), session.userId());
            setOperations().remove(userSessionsKey, sessionId);
            if (Long.valueOf(0L).equals(setOperations().size(userSessionsKey))) {
                redisTemplate.delete(userSessionsKey);
            }
        });
    }

    @Override
    public void deleteAll(long tenantId, long userId) {
        String userSessionsKey = userSessionsKey(tenantId, userId);
        Set<String> sessionIds = setOperations().members(userSessionsKey);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                find(sessionId).ifPresent(session ->
                        redisTemplate.delete(refreshKey(session.refreshTokenHash())));
                redisTemplate.delete(sessionKey(sessionId));
            }
        }
        redisTemplate.delete(userSessionsKey);
    }

    @Override
    public void revokeSession(String sessionId) {
        delete(sessionId);
    }

    @Override
    public void revokeAllUserSessions(long tenantId, long userId) {
        deleteAll(tenantId, userId);
    }

    private void save(AuthSession session, Duration ttl) {
        ValueOperations<String, String> values = valueOperations();
        values.set(sessionKey(session.sessionId()), serialize(session), ttl);
        values.set(refreshKey(session.refreshTokenHash()), session.sessionId(), ttl);
        String userSessionsKey = userSessionsKey(session.tenantId(), session.userId());
        setOperations().add(userSessionsKey, session.sessionId());
        redisTemplate.expire(userSessionsKey, ttl);
    }

    private String serialize(AuthSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Authentication session cannot be serialized", exception);
        }
    }

    private ValueOperations<String, String> valueOperations() {
        return redisTemplate.opsForValue();
    }

    private SetOperations<String, String> setOperations() {
        return redisTemplate.opsForSet();
    }

    private static AuthSession withRefreshTokenHash(AuthSession session, String refreshTokenHash) {
        return new AuthSession(
                session.sessionId(),
                session.tenantId(),
                session.userId(),
                session.username(),
                session.displayName(),
                session.permissions(),
                refreshTokenHash);
    }

    private static String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private static String refreshKey(String refreshTokenHash) {
        return REFRESH_KEY_PREFIX + refreshTokenHash;
    }

    private static String userSessionsKey(long tenantId, long userId) {
        return USER_SESSIONS_KEY_PREFIX + tenantId + ":" + userId;
    }
}
