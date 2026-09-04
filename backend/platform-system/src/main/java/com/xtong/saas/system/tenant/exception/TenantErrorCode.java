package com.xtong.saas.system.tenant.exception;

import com.xtong.saas.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 提供租户查询、状态和受信上下文相关的稳定业务错误码。 */
public enum TenantErrorCode implements ErrorCode {

    TENANT_NOT_FOUND(1201, "租户不存在", HttpStatus.NOT_FOUND),
    TENANT_DISABLED(1202, "租户已禁用", HttpStatus.FORBIDDEN),
    TENANT_CONTEXT_MISSING(1203, "租户上下文缺失", HttpStatus.UNAUTHORIZED);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    TenantErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
