package com.xtong.saas.system.bootstrap.exception;

/** 表示首租户初始化所需配置缺失或不安全，消息只允许包含配置名称。 */
public class BootstrapConfigurationException extends RuntimeException {

    public BootstrapConfigurationException(String message) {
        super(message);
    }
}
