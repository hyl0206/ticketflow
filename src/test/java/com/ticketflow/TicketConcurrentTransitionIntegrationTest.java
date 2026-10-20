package com.ticketflow;

import com.ticketflow.entity.Ticket;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Two real transactions race; exactly one transition and one audit row may commit. */
class TicketConcurrentTransitionIntegrationTest extends TicketServiceTestSupport {
    @Test
    void concurrentStartsHaveOneWinnerAndOneCommittedAudit() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setTicketNo("CC" + UUID.randomUUID().toString().replace("-", "").substring(0, 28));
        ticket.setTitle(marker);
        ticket.setDescription("concurrent transition fixture");
        ticket.setCreatorId(creator.getId());
        ticket.setAssigneeId(creator.getId());
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setVersion(0);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(ticket.getCreatedAt());
        assertEquals(1, ticketMapper.insert(ticket));
        var identity = new AuthenticatedUser(creator.getId(), creator.getUsername(), Set.of("AGENT"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<HttpStatus> task = () -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    identity, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
            try {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                ticketService.startTicket(ticket.getId());
                return HttpStatus.OK;
            } catch (BusinessException conflict) {
                assertTrue(Set.of("TICKET_CONFLICT", "INVALID_TICKET_STATE").contains(conflict.getErrorCode()));
                return conflict.getStatus();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        try {
            Future<HttpStatus> first = pool.submit(task);
            Future<HttpStatus> second = pool.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<HttpStatus> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(HttpStatus.OK::equals).count());
            assertEquals(1, outcomes.stream().filter(HttpStatus.CONFLICT::equals).count());
            assertEquals(TicketStatus.PROCESSING, ticketMapper.selectById(ticket.getId()).getStatus());
            assertEquals(1, ticketMapper.selectById(ticket.getId()).getVersion());
            assertEquals(1, logCount());
            var log = jdbc.queryForMap("SELECT * FROM ticket_operation_log WHERE ticket_id = ?", ticket.getId());
            assertEquals("ASSIGNED", log.get("old_value"));
            assertEquals("PROCESSING", log.get("new_value"));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
