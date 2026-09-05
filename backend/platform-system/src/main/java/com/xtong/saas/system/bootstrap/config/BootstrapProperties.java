package com.xtong.saas.system.bootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 承载仅在系统无租户时才校验和使用的首租户管理员引导配置。 */
@ConfigurationProperties("saas.bootstrap")
public record BootstrapProperties(
        String tenantCode,
        String tenantName,
        String adminUsername,
        String adminPassword) {
}
