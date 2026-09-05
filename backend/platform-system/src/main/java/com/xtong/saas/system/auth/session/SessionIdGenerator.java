package com.xtong.saas.system.auth.session;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** 在服务端生成不可预测、URL 安全的 256 位登录会话标识。 */
@Component
public class SessionIdGenerator {

    private static final int SESSION_ID_BYTES = 32;

    private final SecureRandom secureRandom;

    public SessionIdGenerator() {
        this(new SecureRandom());
    }

    SessionIdGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] randomBytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
