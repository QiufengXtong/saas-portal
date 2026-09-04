package com.xtong.saas.system.auth.session;

import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.auth.model.AuthSession;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 以 Redis Lua 原子维护会话、Refresh 摘要和多设备用户索引。 */
@Service
@Primary
public class RedisSessionStore implements SessionStore, SessionRevocationService {

    private static final String SESSION_KEY_PREFIX = "saas:portal:auth:session:";
    private static final String REFRESH_KEY_PREFIX = "saas:portal:auth:refresh:";
    private static final String USER_SESSIONS_KEY_PREFIX = "saas:portal:auth:user-sessions:";

    /** 原子拒绝会话或摘要碰撞，并同时创建会话、摘要映射和用户索引。 */
    private static final DefaultRedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            local sessionCreated = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[3])
            if not sessionCreated then
                return 0
            end
            local refreshCreated = redis.call('SET', KEYS[2], ARGV[2], 'NX', 'PX', ARGV[3])
            if not refreshCreated then
                redis.call('DEL', KEYS[1])
                return 0
            end
            redis.call('SADD', KEYS[3], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[3])
            return 1
            """, Long.class);

    /** 原子校验旧摘要与有效会话、写入新摘要、删除旧摘要并续期用户索引。 */
    private static final DefaultRedisScript<String> ROTATE_REFRESH_SCRIPT = new DefaultRedisScript<>("""
            if KEYS[1] == KEYS[2] or redis.call('EXISTS', KEYS[2]) == 1 then
                return false
            end
            local sessionId = redis.call('GET', KEYS[1])
            if not sessionId then
                return false
            end
            local sessionKey = ARGV[1] .. sessionId
            local serialized = redis.call('GET', sessionKey)
            if not serialized then
                redis.call('DEL', KEYS[1])
                return false
            end
            local decoded, stored = pcall(cjson.decode, serialized)
            if not decoded or type(stored) ~= 'table'
                    or stored.sessionId ~= sessionId
                    or not stored.tenantId
                    or not stored.userId
                    or stored.refreshTokenHash ~= ARGV[3] then
                redis.call('DEL', KEYS[1])
                return false
            end
            stored.refreshTokenHash = ARGV[4]
            local rotated = cjson.encode(stored)
            redis.call('SET', sessionKey, rotated, 'PX', ARGV[5])
            redis.call('SET', KEYS[2], sessionId, 'PX', ARGV[5])
            redis.call('DEL', KEYS[1])
            local userKey = ARGV[2] .. tostring(stored.tenantId) .. ':' .. tostring(stored.userId)
            redis.call('SADD', userKey, sessionId)
            redis.call('PEXPIRE', userKey, ARGV[5])
            return rotated
            """, String.class);

    /** 原子删除单会话、其当前 Refresh 摘要以及用户集合成员。 */
    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            local serialized = redis.call('GET', KEYS[1])
            if not serialized then
                return 0
            end
            local decoded, stored = pcall(cjson.decode, serialized)
            if not decoded or type(stored) ~= 'table' then
                redis.call('DEL', KEYS[1])
                return 0
            end
            redis.call('DEL', KEYS[1])
            if stored.refreshTokenHash then
                redis.call('DEL', ARGV[1] .. stored.refreshTokenHash)
            end
            if stored.tenantId and stored.userId and stored.sessionId then
                local userKey = ARGV[2] .. tostring(stored.tenantId) .. ':' .. tostring(stored.userId)
                redis.call('SREM', userKey, stored.sessionId)
                if redis.call('SCARD', userKey) == 0 then
                    redis.call('DEL', userKey)
                end
            end
            return 1
            """, Long.class);

    /** 原子遍历用户索引并删除该用户全部会话及其当前 Refresh 摘要。 */
    private static final DefaultRedisScript<Long> DELETE_ALL_SCRIPT = new DefaultRedisScript<>("""
            local sessionIds = redis.call('SMEMBERS', KEYS[1])
            for _, sessionId in ipairs(sessionIds) do
                local sessionKey = ARGV[1] .. sessionId
                local serialized = redis.call('GET', sessionKey)
                if serialized then
                    local decoded, stored = pcall(cjson.decode, serialized)
                    if decoded and type(stored) == 'table' and stored.refreshTokenHash then
                        redis.call('DEL', ARGV[2] .. stored.refreshTokenHash)
                    end
                    redis.call('DEL', sessionKey)
                end
            end
            redis.call('DEL', KEYS[1])
            return #sessionIds
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean create(AuthSession session, String refreshTokenHash, Duration ttl) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(refreshTokenHash, "refreshTokenHash must not be null");
        String ttlMillis = ttlMillis(ttl);
        StoredAuthSession storedSession = StoredAuthSession.from(session, refreshTokenHash);
        Long result = redisTemplate.execute(
                CREATE_SCRIPT,
                List.of(
                        sessionKey(session.sessionId()),
                        refreshKey(refreshTokenHash),
                        userSessionsKey(session.tenantId(), session.userId())),
                serialize(storedSession),
                session.sessionId(),
                ttlMillis);
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public Optional<AuthSession> find(String sessionId) {
        String serialized = redisTemplate.opsForValue().get(sessionKey(sessionId));
        return deserialize(serialized);
    }

    @Override
    public Optional<AuthSession> rotateRefreshToken(
            String currentRefreshTokenHash,
            String newRefreshTokenHash,
            Duration ttl) {
        Objects.requireNonNull(currentRefreshTokenHash, "currentRefreshTokenHash must not be null");
        Objects.requireNonNull(newRefreshTokenHash, "newRefreshTokenHash must not be null");
        String rotated = redisTemplate.execute(
                ROTATE_REFRESH_SCRIPT,
                List.of(refreshKey(currentRefreshTokenHash), refreshKey(newRefreshTokenHash)),
                SESSION_KEY_PREFIX,
                USER_SESSIONS_KEY_PREFIX,
                currentRefreshTokenHash,
                newRefreshTokenHash,
                ttlMillis(ttl));
        return deserialize(rotated);
    }

    @Override
    public void delete(String sessionId) {
        redisTemplate.execute(
                DELETE_SCRIPT,
                List.of(sessionKey(sessionId)),
                REFRESH_KEY_PREFIX,
                USER_SESSIONS_KEY_PREFIX);
    }

    @Override
    public void deleteAll(long tenantId, long userId) {
        redisTemplate.execute(
                DELETE_ALL_SCRIPT,
                List.of(userSessionsKey(tenantId, userId)),
                SESSION_KEY_PREFIX,
                REFRESH_KEY_PREFIX);
    }

    @Override
    public void revokeSession(String sessionId) {
        delete(sessionId);
    }

    @Override
    public void revokeAllUserSessions(long tenantId, long userId) {
        deleteAll(tenantId, userId);
    }

    private String serialize(StoredAuthSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Authentication session cannot be serialized", exception);
        }
    }

    private Optional<AuthSession> deserialize(String serialized) {
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(serialized, StoredAuthSession.class).toDomain());
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored authentication session cannot be deserialized", exception);
        }
    }

    private static String ttlMillis(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return Long.toString(ttl.toMillis());
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

    /** 以字符串持久化长整型身份、以对象持久化权限，避免 Lua 数值及空数组往返损失。 */
    private record StoredAuthSession(
            String sessionId,
            String tenantId,
            String userId,
            String username,
            String displayName,
            Map<String, Boolean> permissions,
            String refreshTokenHash) {

        private static StoredAuthSession from(AuthSession session, String refreshTokenHash) {
            return new StoredAuthSession(
                    session.sessionId(),
                    Long.toString(session.tenantId()),
                    Long.toString(session.userId()),
                    session.username(),
                    session.displayName(),
                    session.permissions().stream().collect(Collectors.toUnmodifiableMap(
                            Function.identity(), ignored -> Boolean.TRUE)),
                    refreshTokenHash);
        }

        private AuthSession toDomain() {
            return new AuthSession(
                    sessionId,
                    Long.parseLong(tenantId),
                    Long.parseLong(userId),
                    username,
                    displayName,
                    permissions == null ? Set.of() : permissions.keySet(),
                    refreshTokenHash);
        }
    }
}
