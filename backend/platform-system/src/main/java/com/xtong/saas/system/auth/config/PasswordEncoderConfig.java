package com.xtong.saas.system.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 配置 IAM 用户与初始化流程共用的 BCrypt 密码编码器。 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    /** 提供设计规定 strength 为 12 的 BCrypt 编码器。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
