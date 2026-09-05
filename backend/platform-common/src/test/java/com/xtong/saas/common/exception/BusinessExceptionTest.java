package com.xtong.saas.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 验证业务异常保留可安全公开的错误码与消息。
 */
class BusinessExceptionTest {

    @Test
    void shouldRetainErrorCodeAndItsSafeMessage() {
        BusinessException exception = new BusinessException(CommonErrorCode.INVALID_PARAMETER);

        assertSame(CommonErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertEquals("参数不合法", exception.getMessage());
    }
}
