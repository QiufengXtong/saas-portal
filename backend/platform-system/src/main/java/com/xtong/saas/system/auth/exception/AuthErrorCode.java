package com.xtong.saas.system.auth.exception;

import com.xtong.saas.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 定义认证、授权、令牌和会话流程可安全公开的稳定错误码。 */
public enum AuthErrorCode implements ErrorCode {

    UNAUTHENTICATED(1101, "未认证或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1102, "无权执行此操作", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(1103, "租户编码、用户名或密码错误", HttpStatus.UNAUTHORIZED),
    LOGIN_LOCKED(1104, "登录失败次数过多，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_REFRESH_TOKEN(1105, "Refresh Token 无效或已使用", HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED(1106, "登录会话已失效", HttpStatus.UNAUTHORIZED);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthErrorCode(int code, String message, HttpStatus httpStatus) {
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
