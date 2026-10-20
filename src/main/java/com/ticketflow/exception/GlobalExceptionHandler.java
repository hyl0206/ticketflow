package com.ticketflow.exception;

import com.ticketflow.common.ErrorResponse;
import com.ticketflow.common.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception, HttpServletRequest request) {
        return error(exception.getStatus(), exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            BindException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError && fieldError.isBindingFailure()
                        ? fieldError.getField() + "参数格式错误" : error.getDefaultMessage())
                .filter(Objects::nonNull)
                .sorted()
                .distinct()
                .collect(Collectors.joining("；"));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY",
                "请求体格式错误，请检查 JSON 格式和字段类型", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                exception.getName() + "参数格式错误", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage()).filter(Objects::nonNull)
                .sorted().distinct().collect(Collectors.joining("；"));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        if (exception.isForReturnValue()) {
            return handleUnexpectedException(exception, request);
        }
        String message = exception.getAllErrors().stream()
                .map(error -> error.getDefaultMessage()).filter(Objects::nonNull)
                .sorted().distinct().collect(Collectors.joining("；"));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                exception.getParameterName() + "参数不能为空", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "缺少必要请求头", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权执行此操作", request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求资源不存在", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (exception.getSupportedHttpMethods() != null) {
            builder.allow(exception.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        }
        return withError(builder, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "请求方法不支持", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "请求内容类型不支持", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "响应内容类型不支持", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        String traceId = RequestTraceFilter.getOrCreateTraceId(request);
        // Exception messages can contain SQL, credentials, or user input; log only the class.
        log.error("unhandled request failure exceptionType={} traceId={}",
                exception.getClass().getName(), traceId);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorCode,
                                               String message, HttpServletRequest request) {
        return withError(ResponseEntity.status(status), status, errorCode, message, request);
    }

    private ResponseEntity<ErrorResponse> withError(ResponseEntity.BodyBuilder builder, HttpStatus status,
                                                   String errorCode, String message,
                                                   HttpServletRequest request) {
        String traceId = RequestTraceFilter.getOrCreateTraceId(request);
        return builder.header(RequestTraceFilter.TRACE_ID_HEADER, traceId)
                .body(new ErrorResponse(status.value(), errorCode, message, traceId));
    }
}
