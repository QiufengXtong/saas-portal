package com.xtong.saas.system.role.exception;

import com.xtong.saas.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 定义角色管理和菜单授权领域可稳定返回给调用方的错误码。 */
public enum RoleErrorCode implements ErrorCode {

    ROLE_NOT_FOUND(1401, "角色不存在", HttpStatus.NOT_FOUND),
    ROLE_CODE_ALREADY_EXISTS(1402, "角色编码已存在", HttpStatus.CONFLICT),
    ROLE_IN_USE(1403, "角色已关联用户，不能删除", HttpStatus.CONFLICT),
    BUILT_IN_ROLE_PROTECTED(1404, "内置或保留角色不能执行该操作", HttpStatus.FORBIDDEN),
    INVALID_MENU_ASSIGNMENT(1405, "菜单授权不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    RoleErrorCode(int code, String message, HttpStatus httpStatus) {
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
