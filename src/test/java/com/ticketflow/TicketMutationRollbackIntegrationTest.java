package com.ticketflow;

import com.ticketflow.entity.Ticket;
import com.ticketflow.entity.TicketOperationLog;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.mapper.TicketOperationLogMapper;
import com.ticketflow.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Calls the real Spring proxy and checks persistence after the failed service transaction ends. */
class TicketMutationRollbackIntegrationTest extends TicketServiceTestSupport {
    @MockitoBean TicketOperationLogMapper operationLogs;

    @Test
    void failedStatusAuditRollsBackStateVersionAndTimestamp() {
        LocalDateTime original = LocalDateTime.of(2026, 1, 1, 12, 0);
        Ticket ticket = new Ticket();
        ticket.setTicketNo("RB" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 28));
        ticket.setTitle(marker);
        ticket.setDescription("rollback fixture");
        ticket.setCreatorId(creator.getId());
        ticket.setAssigneeId(creator.getId());
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setVersion(0);
        ticket.setCreatedAt(original);
        ticket.setUpdatedAt(original);
        assertEquals(1, ticketMapper.insert(ticket));
        var identity = new AuthenticatedUser(creator.getId(), creator.getUsername(), Set.of("AGENT"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                identity, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
        RuntimeException failure = new RuntimeException("controlled audit failure");
        when(operationLogs.insert(any(TicketOperationLog.class))).thenThrow(failure);

        assertSame(failure, assertThrows(RuntimeException.class, () -> ticketService.startTicket(ticket.getId())));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertEquals(TicketStatus.ASSIGNED, persisted.getStatus());
        assertEquals(0, persisted.getVersion());
        assertEquals(original, persisted.getUpdatedAt());
        assertEquals(0, logCount());
        verify(operationLogs).insert(any(TicketOperationLog.class));
    }
}
