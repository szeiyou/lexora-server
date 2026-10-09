package com.songzy.dicserver.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<String, HttpStatus> ERROR_CODE_STATUS_MAP = Map.ofEntries(
            Map.entry("LLM_API_ERROR", HttpStatus.BAD_GATEWAY),
            Map.entry("ALL_LLM_PROVIDERS_FAILED", HttpStatus.BAD_GATEWAY),
            Map.entry("LLM_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("LLM_PROTOCOL_NOT_SUPPORTED", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("LLM_PROVIDER_CONFIG_INVALID", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("TTS_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED),
            Map.entry("REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED),
            Map.entry("USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT),
            Map.entry("RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS)
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorResponse response = new ErrorResponse(e.getCode(), e.getMessage());
        HttpStatus status = ERROR_CODE_STATUS_MAP.getOrDefault(e.getCode(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorResponse response = new ErrorResponse("VALIDATION_ERROR", message);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException e) {
        ErrorResponse response = new ErrorResponse("VALIDATION_ERROR", "请求体格式无效");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(MissingServletRequestParameterException e) {
        String message = String.format("缺少必需的参数: %s", e.getParameterName());
        ErrorResponse response = new ErrorResponse("MISSING_PARAMETER", message);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = String.format("参数类型错误: %s", e.getName());
        ErrorResponse response = new ErrorResponse("INVALID_PARAMETER_TYPE", message);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        ErrorResponse response = new ErrorResponse("NOT_FOUND", "资源不存在");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOtherExceptions(Exception e) {
        log.error("Unhandled exception", e);
        ErrorResponse response = new ErrorResponse("INTERNAL_ERROR", "服务器内部错误");
        return ResponseEntity.internalServerError().body(response);
    }
}

record ErrorResponse(String code, String message) {}
