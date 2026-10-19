package com.ticketflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.controller.TicketController;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.exception.GlobalExceptionHandler;
import com.ticketflow.service.TicketService;
import com.ticketflow.vo.TicketDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketDetailControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
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
    void existingTicketReturnsDetailWithoutPersistenceVersion() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 19, 9, 0);
        TicketDetailResponse response = new TicketDetailResponse();
        response.setId(10L);
        response.setTicketNo("TF-10");
        response.setTitle("无法登录");
        response.setDescription("登录页面提示错误");
        response.setStatus(TicketStatus.CLOSED);
        response.setPriority(TicketPriority.HIGH);
        response.setCreatorId(1L);
        response.setAssigneeId(2L);
        response.setDueDate(createdAt.plusDays(1));
        response.setCreatedAt(createdAt);
        response.setUpdatedAt(createdAt.plusHours(3));
        response.setResolvedAt(createdAt.plusHours(1));
        response.setClosedAt(createdAt.plusHours(2));
        when(ticketService.getTicketById(10L)).thenReturn(response);

        mockMvc.perform(get("/api/tickets/{id}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ticketNo").value("TF-10"))
                .andExpect(jsonPath("$.title").value("无法登录"))
                .andExpect(jsonPath("$.description").value("登录页面提示错误"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.creatorId").value(1))
                .andExpect(jsonPath("$.assigneeId").value(2))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertEquals(13, objectMapper.readTree(body).size());
                    assertEquals(response, objectMapper.readValue(body, TicketDetailResponse.class));
                });
        verify(ticketService).getTicketById(10L);
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void missingTicketReturnsUnifiedNotFoundResponse() throws Exception {
        when(ticketService.getTicketById(10L)).thenThrow(
                new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));

        mockMvc.perform(get("/api/tickets/{id}", 10L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(result -> {
                    BusinessException exception = assertInstanceOf(BusinessException.class,
                            result.getResolvedException());
                    assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
                });
        verify(ticketService).getTicketById(10L);
        verifyNoMoreInteractions(ticketService);
    }
}
