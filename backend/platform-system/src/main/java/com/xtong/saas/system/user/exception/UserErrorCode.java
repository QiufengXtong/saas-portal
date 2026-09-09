package com.xtong.saas.system.user.exception;

import com.xtong.saas.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 定义用户管理领域可稳定返回给调用方的错误码。 */
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(1301, "用户不存在", HttpStatus.NOT_FOUND),
    USERNAME_ALREADY_EXISTS(1302, "用户名已存在", HttpStatus.CONFLICT),
    INVALID_ROLE_ASSIGNMENT(1303, "角色分配不合法", HttpStatus.BAD_REQUEST),
    CANNOT_OPERATE_CURRENT_USER(1304, "不能操作当前用户", HttpStatus.BAD_REQUEST),
    USER_DISABLED(1306, "用户已禁用", HttpStatus.FORBIDDEN),
    PLATFORM_ADMIN_PROTECTED(1307, "平台管理员不能通过租户用户管理变更", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    UserErrorCode(int code, String message, HttpStatus httpStatus) {
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
