package com.westflow.common.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.westflow.common.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一转换为接口错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数对象校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldErrorItem> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorItem)
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION.FIELD_INVALID", "请求参数校验失败", Map.of(), fieldErrors));
    }

    /**
     * 处理单字段约束校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiErrorResponse.FieldErrorItem> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiErrorResponse.FieldErrorItem(
                        violation.getPropertyPath().toString(),
                        "INVALID",
                        violation.getMessage()))
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION.FIELD_INVALID", "请求参数校验失败", Map.of(), fieldErrors));
    }

    /**
     * 处理未登录异常。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiErrorResponse> handleNotLogin(NotLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("AUTH.UNAUTHORIZED", "未登录或登录已失效", Map.of(), List.of()));
    }

    /**
     * 处理无权限异常。
     */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<ApiErrorResponse> handleNotPermission(NotPermissionException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("AUTH.FORBIDDEN", "无权限访问", Map.of("permission", exception.getPermission()), List.of()));
    }

    /**
     * 处理契约异常。
     */
    @ExceptionHandler(ContractException.class)
    public ResponseEntity<ApiErrorResponse> handleContractException(ContractException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage(), exception.getDetails(), List.of()));
    }

    /**
     * 兜底处理未归类的系统异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        log.error("Unhandled exception in API layer: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("SYS.INTERNAL_ERROR", "系统异常，请稍后重试", Map.of(), List.of()));
    }

    /**
     * 将字段错误映射成统一错误项。
     */
    private ApiErrorResponse.FieldErrorItem toFieldErrorItem(FieldError fieldError) {
        String code = switch (fieldError.getCode()) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            default -> "INVALID";
        };
        return new ApiErrorResponse.FieldErrorItem(fieldError.getField(), code, fieldError.getDefaultMessage());
    }
}
