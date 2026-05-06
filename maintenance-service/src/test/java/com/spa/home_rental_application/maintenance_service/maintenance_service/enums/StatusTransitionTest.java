package com.spa.home_rental_application.maintenance_service.maintenance_service.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionTest {

    @Test
    void open_canTransitionTo_inProgressOrClosed() {
        assertThat(Status.OPEN.canTransitionTo(Status.IN_PROGRESS)).isTrue();
        assertThat(Status.OPEN.canTransitionTo(Status.CLOSED)).isTrue();
        assertThat(Status.OPEN.canTransitionTo(Status.RESOLVED)).isFalse();
        assertThat(Status.OPEN.canTransitionTo(Status.OPEN)).isFalse();
    }

    @Test
    void inProgress_canResolve_orRevert_orClose() {
        assertThat(Status.IN_PROGRESS.canTransitionTo(Status.RESOLVED)).isTrue();
        assertThat(Status.IN_PROGRESS.canTransitionTo(Status.OPEN)).isTrue();
        assertThat(Status.IN_PROGRESS.canTransitionTo(Status.CLOSED)).isTrue();
    }

    @Test
    void resolved_canCloseOrReopen() {
        assertThat(Status.RESOLVED.canTransitionTo(Status.CLOSED)).isTrue();
        assertThat(Status.RESOLVED.canTransitionTo(Status.IN_PROGRESS)).isTrue();
        assertThat(Status.RESOLVED.canTransitionTo(Status.OPEN)).isFalse();
    }

    @Test
    void closed_isTerminal() {
        for (Status s : Status.values()) {
            assertThat(Status.CLOSED.canTransitionTo(s)).isFalse();
        }
    }
}
