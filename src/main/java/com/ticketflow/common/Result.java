package com.ticketflow.common;

/** Successful API response. Failures use ErrorResponse with an appropriate HTTP status. */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "成功", data);
    }
}
