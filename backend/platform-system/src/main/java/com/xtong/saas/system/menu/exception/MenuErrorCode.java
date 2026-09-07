package com.xtong.saas.system.menu.exception;

import com.xtong.saas.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 定义菜单与权限资源领域可向调用方公开的稳定错误码。 */
public enum MenuErrorCode implements ErrorCode {

    MENU_NOT_FOUND(1501, "菜单不存在", HttpStatus.NOT_FOUND),
    PERMISSION_CODE_EXISTS(1502, "权限码已存在", HttpStatus.CONFLICT),
    INVALID_PARENT(1503, "菜单父节点不合法", HttpStatus.BAD_REQUEST),
    MENU_CYCLE(1504, "菜单层级不能形成循环", HttpStatus.BAD_REQUEST),
    MENU_HAS_CHILDREN(1505, "菜单存在子节点，无法执行当前操作", HttpStatus.CONFLICT),
    MENU_ASSIGNED_TO_ROLE(1506, "菜单已分配给角色，无法删除", HttpStatus.CONFLICT),
    BUILT_IN_MENU_PROTECTED(1507, "内置菜单的核心配置受保护", HttpStatus.CONFLICT),
    INVALID_MENU_FIELDS(1508, "菜单类型与配置字段不匹配", HttpStatus.BAD_REQUEST);

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
