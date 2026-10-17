package com.ticketflow.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, exception.getMessage()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            BindException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError && fieldError.isBindingFailure()
                        ? fieldError.getField() + "参数格式错误" : error.getDefaultMessage())
                .sorted()
                .distinct()
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(new ErrorResponse(400, message));
    }

    public record ErrorResponse(int code, String message) {
    }
}
