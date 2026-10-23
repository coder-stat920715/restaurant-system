package com.example.workflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketStatusTest {

    @Test
    void onlyForwardOneStepAtATime() {
        assertThat(TicketStatus.QUEUED.canMoveTo(TicketStatus.PREPARING)).isTrue();
        assertThat(TicketStatus.PREPARING.canMoveTo(TicketStatus.READY)).isTrue();
        assertThat(TicketStatus.READY.canMoveTo(TicketStatus.SERVED)).isTrue();

        assertThat(TicketStatus.QUEUED.canMoveTo(TicketStatus.READY)).isFalse();      // no skipping
        assertThat(TicketStatus.READY.canMoveTo(TicketStatus.PREPARING)).isFalse();   // no going back
        assertThat(TicketStatus.SERVED.canMoveTo(TicketStatus.QUEUED)).isFalse();     // terminal
    }
}
