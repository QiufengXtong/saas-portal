package com.xtong.saas.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 定义可映射到统一响应和 HTTP 状态的错误码契约，供业务模块扩展。
 */
public interface ErrorCode {

    /**
     * 返回稳定公开的业务错误码。
     *
     * @return 业务错误码
     */
    int code();

    /**
     * 返回可安全公开给调用方的错误消息。
     *
     * @return 安全错误消息
     */
    String message();

    /**
     * 返回该错误映射的 HTTP 状态。
     *
     * @return HTTP 状态
     */
    HttpStatus httpStatus();
}
