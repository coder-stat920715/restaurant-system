package com.example.workflow.ticket;

public enum TicketStatus {
    QUEUED, PREPARING, READY, SERVED;

    public boolean canMoveTo(TicketStatus next) {
        return switch (this) {
            case QUEUED -> next == PREPARING;
            case PREPARING -> next == READY;
            case READY -> next == SERVED;
            case SERVED -> false;
        };
    }
}
