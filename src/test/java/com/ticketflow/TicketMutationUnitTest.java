package com.ticketflow;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ticketflow.entity.Ticket;
import com.ticketflow.entity.TicketOperationLog;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.mapper.*;
import com.ticketflow.security.AuthenticatedUser;
import com.ticketflow.security.CurrentUserService;
import com.ticketflow.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketMutationUnitTest {
    @Mock TicketMapper tickets;
    @Mock UserMapper users;
    @Mock TicketOperationLogMapper logs;
    @Mock CurrentUserService current;
    @Mock RoleMapper roles;
    @Mock UserRoleMapper userRoles;
    @InjectMocks TicketServiceImpl service;
    Ticket ticket;

    @BeforeEach
    void fixture() {
        ticket = new Ticket();
        ticket.setId(10L);
        ticket.setCreatorId(1L);
        ticket.setAssigneeId(2L);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setVersion(4);
        ticket.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        lenient().when(current.require()).thenReturn(new AuthenticatedUser(2L, "agent", Set.of("AGENT")));
        lenient().when(tickets.selectById(10L)).thenReturn(ticket);
    }

    @Test
    void compareAndSetConflictNeverWritesSuccessLog() {
        when(tickets.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Ticket>>any())).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class, () -> service.startTicket(10L));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("TICKET_CONFLICT", error.getErrorCode());
        verifyNoInteractions(logs);
        assertEquals(4, ticket.getVersion());
    }

    @Test
    void successfulTransitionWritesActualOperatorAndBothStates() {
        when(tickets.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Ticket>>any())).thenReturn(1);
        when(logs.insert(any(TicketOperationLog.class))).thenReturn(1);
        var result = service.startTicket(10L);
        assertEquals(TicketStatus.PROCESSING, result.getStatus());
        var captor = org.mockito.ArgumentCaptor.forClass(TicketOperationLog.class);
        verify(logs).insert(captor.capture());
        assertEquals(2L, captor.getValue().getOperatorId());
        assertEquals("ASSIGNED", captor.getValue().getOldValue());
        assertEquals("PROCESSING", captor.getValue().getNewValue());
        assertEquals(5, ticket.getVersion());
    }

    @Test
    void otherAgentCannotStartAssignedTicket() {
        when(current.require()).thenReturn(new AuthenticatedUser(3L, "another", Set.of("AGENT")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.startTicket(10L)).getStatus());
        verify(tickets, never()).update(any(), any());
        verifyNoInteractions(logs);
    }

    @Test
    void pureAgentCannotCreateAnUnreadablePersonalTicket() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.createTicket(new com.ticketflow.dto.TicketCreateRequest())).getStatus());
        verifyNoInteractions(users, logs);
        verify(tickets, never()).insert(any(Ticket.class));
    }

    @Test
    void invalidJumpDoesNotReachUpdateOrAudit() {
        ticket.setStatus(TicketStatus.OPEN);
        when(current.require()).thenReturn(new AuthenticatedUser(1L, "owner", Set.of("USER")));
        assertEquals("INVALID_TICKET_STATE", assertThrows(BusinessException.class,
                () -> service.closeTicket(10L)).getErrorCode());
        verify(tickets, never()).update(any(), any());
        verifyNoInteractions(logs);
    }

    @Test
    void failedLogInsertPropagatesToTransactionalBoundary() {
        when(tickets.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Ticket>>any())).thenReturn(1);
        when(logs.insert(any(TicketOperationLog.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.startTicket(10L));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> transitions() {
        return Stream.of(TicketStatus.values()).flatMap(from -> Stream.of(TicketStatus.values())
                .map(to -> org.junit.jupiter.params.provider.Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void allStatePairsMatchDocumentedRules(TicketStatus from, TicketStatus to) {
        boolean allowed = switch (from) {
            case OPEN -> to == TicketStatus.ASSIGNED;
            case ASSIGNED -> to == TicketStatus.PROCESSING;
            case PROCESSING -> to == TicketStatus.RESOLVED;
            case RESOLVED -> to == TicketStatus.CLOSED || to == TicketStatus.PROCESSING;
            case CLOSED -> false;
        };
        assertEquals(allowed, from.canTransitionTo(to));
        assertFalse(from.canTransitionTo(null));
    }
}
