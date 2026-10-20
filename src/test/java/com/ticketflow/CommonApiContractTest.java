package com.ticketflow;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ticketflow.common.RequestTraceFilter;
import com.ticketflow.common.Result;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommonApiContractTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void businessErrorRetainsHttpStatusAndSemanticCategoryWithoutFilter() throws Exception {
        var result = mvc.perform(get("/contract/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.errorCode").value("TICKET_CONFLICT"))
                .andExpect(jsonPath("$.message").value("工单已被修改"))
                .andExpect(jsonPath("$.traceId", matchesPattern("[a-f0-9]{32}")))
                .andReturn();
        assertEquals(result.getRequest().getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE),
                result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"title\":\"ok\",\"priority\":\"SECRET_INVALID_ENUM\"}"})
    void malformedJsonAndUnknownEnumReturnSafeBadRequest(String body) throws Exception {
        mvc.perform(post("/contract/body").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("请求体格式错误，请检查 JSON 格式和字段类型"))
                .andExpect(result -> assertFalse(result.getResponse().getContentAsString()
                        .contains("SECRET_INVALID_ENUM")));
    }

    @Test
    void pathTypeAndRangeFailuresHaveDistinctCategories() throws Exception {
        mvc.perform(get("/contract/id/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("id参数格式错误"));
        mvc.perform(get("/contract/id/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("ID必须大于0"));
    }

    @Test
    void validationMessagesAreSortedAndDeduplicated() throws Exception {
        mvc.perform(post("/contract/body").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("A必填；B必填"));
    }

    @Test
    void constraintViolationsUseSameValidationContract() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new RequiredValue(""));
            var response = new GlobalExceptionHandler().handleConstraintViolation(
                    new ConstraintViolationException(violations), new MockHttpServletRequest());
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("VALIDATION_FAILED", response.getBody().errorCode());
            assertEquals("值不能为空", response.getBody().message());
        }
    }

    @Test
    void accessDenialDoesNotExposeSecurityImplementationDetails() throws Exception {
        mvc.perform(get("/contract/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("无权执行此操作"));
    }

    @Test
    void missingParameterWrongMethodAndWrongContentTypeKeepHttpSemantics() throws Exception {
        mvc.perform(get("/contract/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
        mvc.perform(put("/contract/conflict"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/contract/body").contentType(MediaType.TEXT_PLAIN).content("secret"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void internalFailureProducesTraceableGenericResponseAndSafeLog() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = mvc.perform(get("/contract/failure"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"))
                    .andReturn();
            assertFalse(result.getResponse().getContentAsString().contains("secret-password"));
            assertEquals(1, appender.list.size());
            var event = appender.list.getFirst();
            assertTrue(event.getFormattedMessage().contains("IllegalStateException"));
            assertTrue(event.getFormattedMessage().contains(
                    result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER)));
            assertFalse(event.getFormattedMessage().contains("secret-password"));
            assertNull(event.getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    record RequiredValue(@NotBlank(message = "值不能为空") String value) { }
    record Payload(@NotBlank(message = "B必填") String title,
                   @NotNull(message = "A必填") TicketPriority priority,
                   @NotNull(message = "A必填") String repeated) { }

    @RestController
    static class ContractController {
        @PostMapping("/contract/body")
        Result<Payload> body(@Valid @RequestBody Payload payload) {
            return Result.success(payload);
        }

        @GetMapping("/contract/id/{id}")
        Result<Long> id(@PathVariable("id") @Positive(message = "ID必须大于0") Long id) {
            return Result.success(id);
        }

        @GetMapping("/contract/required")
        Result<String> required(@RequestParam("value") String value) {
            return Result.success(value);
        }

        @GetMapping("/contract/conflict")
        void conflict() {
            throw new BusinessException(HttpStatus.CONFLICT, "TICKET_CONFLICT", "工单已被修改");
        }

        @GetMapping("/contract/forbidden")
        void forbidden() {
            throw new AccessDeniedException("private authorization implementation");
        }

        @GetMapping("/contract/failure")
        void failure() {
            throw new IllegalStateException("secret-password database internals");
        }
    }
}
