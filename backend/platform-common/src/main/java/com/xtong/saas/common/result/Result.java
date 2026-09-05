package com.xtong.saas.common.result;

import com.xtong.saas.common.exception.ErrorCode;

/**
 * 提供所有 HTTP API 复用的统一成功和失败响应载体，不承载具体业务语义。
 *
 * @param code 对调用方稳定公开的业务结果码
 * @param message 对调用方安全公开的结果消息
 * @param data 响应数据；无数据时为 {@code null}
 * @param <T> 响应数据类型
 */
public record Result<T>(int code, String message, T data) {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 创建不包含数据的标准成功响应。
     *
     * @return 无数据的成功响应
     */
    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /**
     * 创建包含调用方数据的标准成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 根据错误码创建不包含数据的失败响应。
     *
     * @param errorCode 可安全公开的错误码
     * @return 失败响应
     */
    public static Result<Void> failure(ErrorCode errorCode) {
        return failure(errorCode.code(), errorCode.message());
    }

    /**
     * 根据显式的安全错误码和消息创建不包含数据的失败响应。
     *
     * @param code 对调用方稳定公开的错误码
     * @param message 对调用方安全公开的错误消息
     * @return 失败响应
     */
    public static Result<Void> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
