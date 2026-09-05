package com.xtong.saas.common.mybatis;

import java.util.OptionalLong;

/**
 * 为通用审计填充提供当前操作者 ID，具体认证实现由上层模块注入。
 */
public interface AuditorProvider {

    /**
     * 获取当前请求的操作者 ID。
     *
     * @return 已认证操作者 ID；无认证上下文时为空
     */
    OptionalLong currentAuditorId();
}
