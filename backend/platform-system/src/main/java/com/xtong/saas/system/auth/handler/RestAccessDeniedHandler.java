package com.xtong.saas.system.auth.handler;

import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.auth.exception.AuthErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 将过滤器与方法授权产生的权限不足结果统一输出为 403 JSON。 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(AuthErrorCode.FORBIDDEN.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.failure(AuthErrorCode.FORBIDDEN));
    }

    /** 将 MVC 方法授权异常映射为与过滤器授权失败一致的响应。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleMethodAccessDenied(AccessDeniedException accessDeniedException) {
        return ResponseEntity.status(AuthErrorCode.FORBIDDEN.httpStatus())
                .body(Result.failure(AuthErrorCode.FORBIDDEN));
    }
}
