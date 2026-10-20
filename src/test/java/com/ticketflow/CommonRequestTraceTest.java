package com.ticketflow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ticketflow.common.RequestTraceFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CommonRequestTraceTest {

    @Test
    void traceIsGeneratedAndMetadataLogExcludesClientSecrets() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(RequestTraceFilter.TRACE_ID_HEADER, "forged\ntrace");
        request.addHeader("Authorization", "Bearer secret-token");
        request.setQueryString("password=secret-query");
        request.setContent("secret-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(RequestTraceFilter.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            new RequestTraceFilter().doFilter(request, response, (req, res) -> {
                String traceId = response.getHeader(RequestTraceFilter.TRACE_ID_HEADER);
                assertNotNull(traceId);
                assertTrue(traceId.matches("[a-f0-9]{32}"));
                assertEquals(traceId, MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY));
                response.setStatus(201);
            });
            assertNull(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY));
            assertEquals(1, appender.list.size());
            String message = appender.list.getFirst().getFormattedMessage();
            assertTrue(message.contains("method=POST path=/api/auth/login status=201"));
            assertTrue(message.contains(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)));
            assertFalse(message.contains("secret"));
            assertFalse(message.contains("forged"));
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(appender);
            appender.stop();
            MDC.remove(RequestTraceFilter.TRACE_ID_MDC_KEY);
        }
    }

    @Test
    void failureRestoresExistingMdcAndKeepsOriginalException() {
        MDC.put(RequestTraceFilter.TRACE_ID_MDC_KEY, "outer-context");
        var failure = new ServletException("private-detail");
        try {
            var thrown = assertThrows(ServletException.class, () -> new RequestTraceFilter().doFilter(
                    new MockHttpServletRequest("GET", "/api/tickets"), new MockHttpServletResponse(),
                    (request, response) -> { throw failure; }));
            assertSame(failure, thrown);
            assertEquals("outer-context", MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY));
        } finally {
            MDC.remove(RequestTraceFilter.TRACE_ID_MDC_KEY);
        }
    }
}
