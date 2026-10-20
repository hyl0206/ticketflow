package com.ticketflow.common;

/** Machine-readable error category and a trace ID, without exception implementation details. */
public record ErrorResponse(int code, String errorCode, String message, String traceId) {
}
