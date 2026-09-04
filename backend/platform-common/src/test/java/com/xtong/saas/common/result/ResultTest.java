package com.xtong.saas.common.result;

import com.xtong.saas.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证统一响应在成功和失败场景下公开的稳定契约。
 */
class ResultTest {

    @Test
    void shouldCreateSuccessResultWithoutData() {
        Result<Void> result = Result.success();

        assertEquals(0, result.code());
        assertEquals("success", result.message());
        assertNull(result.data());
    }

    @Test
    void shouldCreateSuccessResultWithData() {
        Result<String> result = Result.success("ready");

        assertEquals(0, result.code());
        assertEquals("success", result.message());
        assertEquals("ready", result.data());
    }

    @Test
    void shouldCreateFailureResultFromErrorCode() {
        Result<Void> result = Result.failure(CommonErrorCode.INVALID_PARAMETER);

        assertEquals(1001, result.code());
        assertEquals("参数不合法", result.message());
        assertNull(result.data());
    }

    @Test
    void shouldCreateFailureResultFromExplicitCodeAndMessage() {
        Result<Void> result = Result.failure(1999, "安全错误消息");

        assertEquals(1999, result.code());
        assertEquals("安全错误消息", result.message());
        assertNull(result.data());
    }
}
