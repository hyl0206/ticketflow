package com.ticketflow;

import com.ticketflow.controller.TicketController;
import com.ticketflow.dto.TicketQueryRequest;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.GlobalExceptionHandler;
import com.ticketflow.service.TicketService;
import com.ticketflow.vo.TicketPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketQueryControllerTest {

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
    void omittedParametersUseDefaults() throws Exception {
        when(ticketService.queryTickets(any())).thenReturn(
                new TicketPageResponse(List.of(), 0, 1, 10, 0));

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.pages").value(0));

        ArgumentCaptor<TicketQueryRequest> query = ArgumentCaptor.forClass(TicketQueryRequest.class);
        verify(ticketService).queryTickets(query.capture());
        assertEquals(1, query.getValue().getPageNum());
        assertEquals(10, query.getValue().getPageSize());
    }

    @Test
    void bindsAllQueryParameters() throws Exception {
        when(ticketService.queryTickets(any())).thenReturn(
                new TicketPageResponse(List.of(), 0, 2, 3, 0));

        mockMvc.perform(get("/api/tickets").param("pageNum", "2").param("pageSize", "3")
                        .param("keyword", " 网络 ").param("status", "PROCESSING")
                        .param("priority", "URGENT").param("creatorId", "12"))
                .andExpect(status().isOk());

        ArgumentCaptor<TicketQueryRequest> query = ArgumentCaptor.forClass(TicketQueryRequest.class);
        verify(ticketService).queryTickets(query.capture());
        assertEquals(2, query.getValue().getPageNum());
        assertEquals(3, query.getValue().getPageSize());
        assertEquals(" 网络 ", query.getValue().getKeyword());
        assertEquals(TicketStatus.PROCESSING, query.getValue().getStatus());
        assertEquals(TicketPriority.URGENT, query.getValue().getPriority());
        assertEquals(12L, query.getValue().getCreatorId());
    }

    @ParameterizedTest
    @CsvSource({
            "pageNum, 0, 页码必须大于等于1",
            "pageNum, -1, 页码必须大于等于1",
            "pageSize, 0, 每页条数必须大于等于1",
            "pageSize, -1, 每页条数必须大于等于1",
            "pageSize, 101, 每页条数不能大于100",
            "creatorId, 0, 创建人ID必须大于0",
            "creatorId, -1, 创建人ID必须大于0",
            "pageNum, '', 页码不能为空",
            "pageSize, '', 每页条数不能为空"
    })
    void invalidRangesReturnUnifiedBadRequest(String field, String value, String message)
            throws Exception {
        assertBadRequest(field, value, message);
    }

    @ParameterizedTest
    @CsvSource({
            "status, UNKNOWN", "status, open", "status, 1",
            "priority, UNKNOWN", "priority, high", "priority, 1",
            "pageNum, abc", "pageNum, 2147483648", "pageSize, abc",
            "creatorId, abc", "creatorId, 9223372036854775808"
    })
    void invalidTypesReturnUnifiedBadRequest(String field, String value) throws Exception {
        assertBadRequest(field, value, field + "参数格式错误");
    }

    private void assertBadRequest(String field, String value, String message) throws Exception {
        mockMvc.perform(get("/api/tickets").param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(ticketService);
    }
}
