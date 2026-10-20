package com.ticketflow.enums;

public enum TicketStatus {

    OPEN,
    ASSIGNED,
    PROCESSING,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(TicketStatus target) {
        return switch (this) {
            case OPEN -> target == ASSIGNED;
            case ASSIGNED -> target == PROCESSING;
            case PROCESSING -> target == RESOLVED;
            case RESOLVED -> target == CLOSED || target == PROCESSING;
            case CLOSED -> false;
        };
    }
}
