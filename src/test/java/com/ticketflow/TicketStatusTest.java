package com.ticketflow;

import com.ticketflow.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TicketStatusTest {

    @Test
    void testStatusTransition() {

        assertTrue(
                TicketStatus.OPEN
                        .canTransitionTo(TicketStatus.ASSIGNED)
        );

        assertTrue(
                TicketStatus.ASSIGNED
                        .canTransitionTo(TicketStatus.PROCESSING)
        );

        assertTrue(
                TicketStatus.PROCESSING
                        .canTransitionTo(TicketStatus.RESOLVED)
        );

        assertTrue(
                TicketStatus.RESOLVED
                        .canTransitionTo(TicketStatus.CLOSED)
        );

        assertFalse(
                TicketStatus.OPEN
                        .canTransitionTo(TicketStatus.CLOSED)
        );

        assertFalse(
                TicketStatus.CLOSED
                        .canTransitionTo(TicketStatus.PROCESSING)
        );
    }
}