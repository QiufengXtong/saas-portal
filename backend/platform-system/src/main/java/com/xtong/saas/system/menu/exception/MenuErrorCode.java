package com.xtong.saas.system.menu.exception;

import com.xtong.saas.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 定义菜单与权限资源领域可向调用方公开的稳定错误码。 */
public enum MenuErrorCode implements ErrorCode {

    MENU_NOT_FOUND(1501, "菜单不存在", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    MenuErrorCode(int code, String message, HttpStatus httpStatus) {
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
