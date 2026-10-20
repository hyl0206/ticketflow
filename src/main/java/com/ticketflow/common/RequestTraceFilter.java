package com.ticketflow.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Logs request metadata only: no credentials, body, headers, or query string. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[a-f0-9]{32}");
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    /** Also works in standalone MVC tests and security entry points without a filter chain. */
    public static String getOrCreateTraceId(HttpServletRequest request) {
        Object existing = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (existing instanceof String traceId && SAFE_TRACE_ID.matcher(traceId).matches()) {
            return traceId;
        }
        // Generate our own ID; an arbitrary client-supplied header must not enter logs.
        String traceId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        return traceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = getOrCreateTraceId(request);
        String previousTraceId = MDC.get(TRACE_ID_MDC_KEY);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        long start = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            log.info("request method={} path={} status={} durationMs={} traceId={}",
                    safeLogValue(request.getMethod()), safeLogValue(request.getRequestURI()),
                    status, elapsedMs, traceId);
            if (previousTraceId == null) {
                MDC.remove(TRACE_ID_MDC_KEY);
            } else {
                MDC.put(TRACE_ID_MDC_KEY, previousTraceId);
            }
        }
    }

    private static String safeLogValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\p{Cntrl}]", "_");
    }
}
