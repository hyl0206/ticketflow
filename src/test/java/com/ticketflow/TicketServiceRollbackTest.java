package com.ticketflow;

import com.ticketflow.entity.TicketOperationLog;
import com.ticketflow.mapper.TicketOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketServiceRollbackTest extends TicketServiceTestSupport {

    @MockitoBean
    private TicketOperationLogMapper operationLogMapper;

    @Test
    void logInsertFailureRollsBackAlreadyInsertedTicket() {
        RuntimeException failure = new RuntimeException("Simulated operation log insert failure");
        AtomicReference<Long> insertedTicketId = new AtomicReference<>();
        assertEquals(0, ticketCount());
        long logsBefore = logCount();
        doAnswer(invocation -> {
            TicketOperationLog log = invocation.getArgument(0);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertNotNull(log.getTicketId());
            insertedTicketId.set(log.getTicketId());
            // Query the real database in the service transaction: proves the INSERT happened.
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ticket WHERE id = ? AND title = ?",
                    Long.class, log.getTicketId(), marker));
            assertEquals(creator.getId(), log.getOperatorId());
            throw failure;
        }).when(operationLogMapper).insert(any(TicketOperationLog.class));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ticketService.createTicket(request()));

        assertSame(failure, thrown);
        verify(operationLogMapper, times(1)).insert(any(TicketOperationLog.class));
        assertNotNull(insertedTicketId.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        // These queries run after the service transaction has ended, before cleanup.
        assertNull(ticketMapper.selectById(insertedTicketId.get()));
        assertEquals(0, ticketCount());
        assertEquals(logsBefore, logCount());
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_operation_log WHERE ticket_id = ?",
                Long.class, insertedTicketId.get()));
    }
}
