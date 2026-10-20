package com.ticketflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketflow.controller.TicketController;
import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.exception.GlobalExceptionHandler;
import com.ticketflow.exception.UserDisabledException;
import com.ticketflow.exception.UserNotFoundException;
import com.ticketflow.service.TicketService;
import com.ticketflow.vo.TicketCreateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TicketService ticketService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(ticketService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validRequestReturnsCreated() throws Exception {
        TicketCreateResponse response = new TicketCreateResponse();
        response.setId(10L);
        response.setTicketNo("TF-10");
        response.setStatus(TicketStatus.OPEN);
        when(ticketService.createTicket(any(TicketCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.ticketNo").value("TF-10"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
        verify(ticketService).createTicket(any(TicketCreateRequest.class));
    }

    @ParameterizedTest
    @CsvSource({"missing, 创建人不存在", "disabled, 用户已被禁用", "business, 业务失败"})
    void businessExceptionsReturnUnifiedError(String type, String message) throws Exception {
        BusinessException exception = switch (type) {
            case "missing" -> new UserNotFoundException(message);
            case "disabled" -> new UserDisabledException(message);
            default -> new BusinessException(message);
        };
        when(ticketService.createTicket(any(TicketCreateRequest.class))).thenThrow(exception);

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message));
    }

    @ParameterizedTest
    @CsvSource({
            "title, null, 工单标题不能为空",
            "title, empty, 工单标题不能为空",
            "title, blank, 工单标题不能为空",
            "description, null, 工单描述不能为空",
            "description, empty, 工单描述不能为空",
            "description, blank, 工单描述不能为空",
            "priority, null, 工单优先级不能为空"
    })
    void invalidRequestReturnsValidationErrorBeforeCallingService(
            String field, String value, String message) throws Exception {
        ObjectNode request = validRequest();
        switch (value) {
            case "null" -> request.remove(field);
            case "empty" -> request.put(field, "");
            case "blank" -> request.put(field, "   ");
            default -> request.put(field, Long.parseLong(value));
        }

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(result -> assertInstanceOf(
                        org.springframework.web.bind.MethodArgumentNotValidException.class,
                        result.getResolvedException()));
        verifyNoInteractions(ticketService);
    }

    @Test
    void multipleValidationFailuresReturnAllMessages() throws Exception {
        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("工单标题不能为空")))
                .andExpect(jsonPath("$.message", containsString("工单描述不能为空")))
                .andExpect(jsonPath("$.message", containsString("工单优先级不能为空")));
        verifyNoInteractions(ticketService);
    }

    @ParameterizedTest
    @CsvSource({"creatorId, 0", "creatorId, -1", "creatorId, 99", "operatorId, 1", "unexpected, 1"})
    void unknownFieldsCannotSupplyAnIdentity(String field, long value) throws Exception {
        ObjectNode request = validRequest().put(field, value);
        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void evenNullCreatorIdIsAnUnsupportedInputField() throws Exception {
        ObjectNode request = validRequest().putNull("creatorId");
        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        verifyNoInteractions(ticketService);
    }

    private ObjectNode validRequest() {
        return objectMapper.createObjectNode()
                .put("title", "无法登录")
                .put("description", "登录页面提示错误")
                .put("priority", "HIGH");
    }
}
