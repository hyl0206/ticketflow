package com.ticketflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.common.ErrorResponse;
import com.ticketflow.common.RequestTraceFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, 401, "UNAUTHENTICATED", "请登录或重新获取有效令牌");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        write(request, response, 403, "ACCESS_DENIED", "无权执行此操作");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status,
                       String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String traceId = RequestTraceFilter.getOrCreateTraceId(request);
        response.setHeader("X-Trace-Id", traceId);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(status, errorCode, message,
                traceId));
    }
}
