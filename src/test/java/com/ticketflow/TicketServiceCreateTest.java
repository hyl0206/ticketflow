package com.ticketflow;

import com.ticketflow.entity.Ticket;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.vo.TicketCreateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;

class TicketServiceCreateTest extends TicketServiceTestSupport {

    @Test
    void validCreationCommitsTicketAndCreateLog() {
        assertEquals(0, ticketCount());
        assertEquals(0, logCount());

        TicketCreateResponse response = ticketService.createTicket(request());

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertNotNull(response.getId());
        assertEquals(1, ticketCount());
        Ticket saved = ticketMapper.selectById(response.getId());
        assertNotNull(saved);
        assertEquals(marker, saved.getTitle());
        assertEquals(response.getTicketNo(), saved.getTicketNo());
        assertEquals(creator.getId(), saved.getCreatorId());
        assertEquals(TicketStatus.OPEN, saved.getStatus());
        assertEquals(TicketPriority.HIGH, saved.getPriority());
        assertEquals(0, saved.getVersion());
        assertEquals(1, logCount());
        var log = jdbc.queryForMap("SELECT * FROM ticket_operation_log WHERE ticket_id = ?",
                response.getId());
        assertEquals(creator.getId(), ((Number) log.get("operator_id")).longValue());
        assertEquals("CREATE", log.get("operation_type"));
        assertNull(log.get("old_value"));
        assertEquals("OPEN", log.get("new_value"));
    }

    @Test
    void missingCreatorRejectsCreationWithoutWrites() {
        // Use a deleted fixture ID rather than assuming a fixed ID is absent.
        assertEquals(1, userMapper.deleteById(creator.getId()));
        assertNull(userMapper.selectById(creator.getId()));
        assertEquals(0, ticketCount());
        long logsBefore = logCount();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ticketService.createTicket(request()));

        assertEquals("创建人不存在", exception.getMessage());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0, ticketCount());
        assertEquals(logsBefore, logCount());
    }
}
