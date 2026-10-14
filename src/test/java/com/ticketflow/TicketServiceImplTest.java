package com.ticketflow;

import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.entity.Ticket;
import com.ticketflow.entity.TicketOperationLog;
import com.ticketflow.entity.User;
import com.ticketflow.enums.TicketOperationType;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.UserDisabledException;
import com.ticketflow.exception.UserNotFoundException;
import com.ticketflow.mapper.TicketMapper;
import com.ticketflow.mapper.TicketOperationLogMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock private TicketMapper ticketMapper;
    @Mock private UserMapper userMapper;
    @Mock private TicketOperationLogMapper operationLogMapper;
    @InjectMocks private TicketServiceImpl ticketService;

    private TicketCreateRequest request;

    @BeforeEach
    void setUp() {
        request = new TicketCreateRequest();
        request.setTitle("无法登录");
        request.setDescription("登录页面提示错误");
        request.setPriority(TicketPriority.HIGH);
        request.setCreatorId(1L);
    }

    @Test
    void createsTicketAndOperationLogWithGeneratedFields() {
        User creator = new User();
        creator.setId(1L);
        creator.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(creator);
        when(ticketMapper.insert(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(10L);
            return 1;
        });
        when(operationLogMapper.insert(any(TicketOperationLog.class))).thenReturn(1);

        var response = ticketService.createTicket(request);

        ArgumentCaptor<Ticket> tickets = ArgumentCaptor.forClass(Ticket.class);
        ArgumentCaptor<TicketOperationLog> logs = ArgumentCaptor.forClass(TicketOperationLog.class);
        var order = inOrder(userMapper, ticketMapper, operationLogMapper);
        order.verify(userMapper).selectById(1L);
        order.verify(ticketMapper).insert(tickets.capture());
        order.verify(operationLogMapper).insert(logs.capture());
        Ticket saved = tickets.getValue();
        assertEquals(request.getTitle(), saved.getTitle());
        assertEquals(request.getDescription(), saved.getDescription());
        assertEquals(request.getPriority(), saved.getPriority());
        assertEquals(1L, saved.getCreatorId());
        assertNotNull(saved.getTicketNo());
        assertFalse(saved.getTicketNo().isBlank());
        assertEquals(TicketStatus.OPEN, saved.getStatus());
        assertEquals(0, saved.getVersion());
        assertNotNull(saved.getCreatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        TicketOperationLog log = logs.getValue();
        assertEquals(saved.getId(), log.getTicketId());
        assertEquals(1L, log.getOperatorId());
        assertEquals(TicketOperationType.CREATE, log.getOperationType());
        assertNull(log.getOldValue());
        assertEquals("OPEN", log.getNewValue());
        assertEquals(saved.getCreatedAt(), log.getCreatedAt());
        assertEquals(saved.getId(), response.getId());
        assertEquals(saved.getTicketNo(), response.getTicketNo());
        assertEquals(saved.getTitle(), response.getTitle());
        assertEquals(saved.getStatus(), response.getStatus());
        assertEquals(saved.getPriority(), response.getPriority());
        assertEquals(saved.getCreatorId(), response.getCreatorId());
        assertEquals(saved.getCreatedAt(), response.getCreatedAt());
    }

    @Test
    void missingCreatorRejectsCreationBeforeWrites() {
        when(userMapper.selectById(1L)).thenReturn(null);

        var exception = assertThrows(UserNotFoundException.class,
                () -> ticketService.createTicket(request));

        assertEquals("创建人不存在", exception.getMessage());
        verifyNoInteractions(ticketMapper, operationLogMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 2})
    void disabledCreatorRejectsCreationBeforeWrites(Integer status) {
        User creator = new User();
        creator.setId(1L);
        creator.setStatus(status);
        when(userMapper.selectById(1L)).thenReturn(creator);

        var exception = assertThrows(UserDisabledException.class,
                () -> ticketService.createTicket(request));

        assertEquals("用户已被禁用", exception.getMessage());
        verifyNoInteractions(ticketMapper, operationLogMapper);
    }
}
