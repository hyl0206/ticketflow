package com.ticketflow;

import com.ticketflow.entity.Ticket;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.UserDisabledException;
import com.ticketflow.exception.UserNotFoundException;
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
        assertFalse(saved.getTicketNo().isBlank());
        assertEquals(request().getDescription(), saved.getDescription());
        assertNotNull(saved.getCreatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
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

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> ticketService.createTicket(request()));

        assertEquals("创建人不存在", exception.getMessage());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0, ticketCount());
        assertEquals(logsBefore, logCount());
    }

    @Test
    void disabledCreatorRejectsCreationWithoutWrites() {
        creator.setStatus(0);
        assertEquals(1, userMapper.updateById(creator));
        assertEquals(0, ticketCount());
        long logsBefore = logCount();

        UserDisabledException exception = assertThrows(UserDisabledException.class,
                () -> ticketService.createTicket(request()));

        assertEquals("用户已被禁用", exception.getMessage());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0, ticketCount());
        assertEquals(logsBefore, logCount());
    }
}
