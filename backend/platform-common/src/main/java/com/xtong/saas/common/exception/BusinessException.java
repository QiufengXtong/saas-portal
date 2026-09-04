package com.xtong.saas.common.exception;

/**
 * 表示可安全转换为统一响应的业务异常，并保留其声明的错误码。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用错误码的安全消息创建业务异常。
     *
     * @param errorCode 可安全公开的业务错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 返回用于统一错误响应的业务错误码。
     *
     * @return 业务错误码
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
