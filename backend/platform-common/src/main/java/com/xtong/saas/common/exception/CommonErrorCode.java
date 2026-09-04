package com.xtong.saas.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 提供与具体业务领域无关的通用参数和系统错误码。
 */
public enum CommonErrorCode implements ErrorCode {

    INVALID_PARAMETER(1001, "参数不合法", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(1002, "请求内容格式错误", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(1003, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(int code, String message, HttpStatus httpStatus) {
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
