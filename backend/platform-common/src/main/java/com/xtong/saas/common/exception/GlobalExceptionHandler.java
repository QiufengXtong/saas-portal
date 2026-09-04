package com.xtong.saas.common.exception;

import com.xtong.saas.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;

/**
 * 将 Controller 链路异常转换为统一且不泄露内部信息的响应。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 保留业务异常声明的错误码和 HTTP 状态。
     *
     * @param exception 业务异常
     * @return 声明错误码对应的统一响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        return failure(exception.getErrorCode());
    }

    /**
     * 将请求体字段校验错误映射为首个可安全公开的字段消息。
     *
     * @param exception Spring MVC 参数校验异常
     * @return 参数错误的统一响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(this::hasText)
                .findFirst()
                .orElse(CommonErrorCode.INVALID_PARAMETER.message());
        return failure(CommonErrorCode.INVALID_PARAMETER, message);
    }

    /**
     * 将方法参数约束错误映射为稳定且不包含 rejected value 的公开消息。
     *
     * @param exception Jakarta Validation 约束异常
     * @return 参数错误的统一响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .sorted(Comparator.comparing((ConstraintViolation<?> violation) -> violation.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .map(ConstraintViolation::getMessage)
                .filter(this::hasText)
                .findFirst()
                .orElse(CommonErrorCode.INVALID_PARAMETER.message());
        return failure(CommonErrorCode.INVALID_PARAMETER, message);
    }

    /**
     * 将无法解析的 JSON 或请求体映射为固定格式错误，避免公开原始内容。
     *
     * @param exception 请求体解析异常
     * @return 格式错误的统一响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        return failure(CommonErrorCode.MALFORMED_REQUEST);
    }

    /**
     * 记录未预期异常并仅向调用方公开通用系统错误。
     *
     * @param exception 未预期的 Controller 链路异常
     * @return 通用系统错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request exception", exception);
        return failure(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<Void>> failure(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.failure(errorCode));
    }

    private ResponseEntity<Result<Void>> failure(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.failure(errorCode.code(), message));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
