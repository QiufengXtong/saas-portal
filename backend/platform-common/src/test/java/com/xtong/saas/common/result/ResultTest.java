package com.xtong.saas.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultTest {

    @Test
    void successCreatesStandardSuccessResponse() {
        Result<String> result = Result.success("ready");

        assertEquals(0, result.code());
        assertEquals("success", result.message());
        assertEquals("ready", result.data());
    }
}
