package com.xtong.saas.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 Controller 异常被映射为不泄露内部详情的统一 HTTP 响应。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapBusinessExceptionToDeclaredStatus() {
        var response = handler.handleBusinessException(
                new BusinessException(CommonErrorCode.INVALID_PARAMETER));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1001, response.getBody().code());
        assertEquals("参数不合法", response.getBody().message());
    }

    @Test
    void shouldReturnFirstReadableFieldMessageForMethodArgumentValidation() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new LoginCommand(""), "loginCommand");
        bindingResult.addError(new FieldError("loginCommand", "username", "sensitive-input", false,
                null, null, "用户名不能为空"));
        MethodParameter parameter = methodParameter();

        var response = handler.handleMethodArgumentNotValidException(
                new MethodArgumentNotValidException(parameter, bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1001, response.getBody().code());
        assertEquals("用户名不能为空", response.getBody().message());
        assertFalse(response.getBody().message().contains("sensitive-input"));
    }

    @Test
    void shouldReturnConstraintMessageWithoutRejectedValue() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new LoginCommand(""));

        var response = handler.handleConstraintViolationException(new ConstraintViolationException(violations));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1001, response.getBody().code());
        assertEquals("用户名不能为空", response.getBody().message());
    }

    @Test
    void shouldMapUnreadableMessageToBadRequestWithoutInternalDetails() {
        var response = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("invalid JSON: password=Secret123", emptyInputMessage()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1002, response.getBody().code());
        assertEquals("请求内容格式错误", response.getBody().message());
        assertFalse(response.getBody().message().contains("Secret123"));
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() {
        var response = handler.handleUnexpectedException(
                new IllegalStateException("jdbc:mysql://user:password@host"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(1003, response.getBody().code());
        assertEquals("系统内部错误", response.getBody().message());
        assertFalse(response.getBody().message().contains("password"));
    }

    private MethodParameter methodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("accept", LoginCommand.class);
        return new MethodParameter(method, 0);
    }

    private void accept(LoginCommand command) {
    }

    private HttpInputMessage emptyInputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }
        };
    }

    /**
     * 提供 Jakarta Validation 约束测试所需的最小请求模型。
     *
     * @param username 待校验的用户名
     */
    private record LoginCommand(@NotBlank(message = "用户名不能为空") String username) {
    }
}
