package com.xtong.saas.system.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证用户密码配置提供符合 IAM 强度要求的 BCrypt 编码器。 */
class PasswordEncoderConfigTest {

    @Test
    void shouldEncodeAndVerifyPasswordWithBcryptStrengthTwelve() {
        PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

        String hash = passwordEncoder.encode("Password123");

        assertThat(hash).startsWith("$2");
        assertThat(Integer.parseInt(hash.substring(4, 6))).isEqualTo(12);
        assertThat(passwordEncoder.matches("Password123", hash)).isTrue();
    }
}
