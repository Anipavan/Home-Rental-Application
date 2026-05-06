package com.spa.home_rental_application.maintenance_service.maintenance_service.enums;

import java.util.Map;
import java.util.Set;

/**
 * Status state machine. Allowed transitions are codified here so the
 * service layer can reject illegal moves cleanly.
 *
 * OPEN → IN_PROGRESS → RESOLVED → CLOSED
 *  └→ CLOSED (e.g. flat vacated before any work started)
 *  IN_PROGRESS → OPEN (re-open if an assignment is reverted)
 *  RESOLVED → IN_PROGRESS (re-open if tenant disputes resolution)
 */
public enum Status {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            OPEN,        Set.of(IN_PROGRESS, CLOSED),
            IN_PROGRESS, Set.of(RESOLVED, OPEN, CLOSED),
            RESOLVED,    Set.of(CLOSED, IN_PROGRESS),
            CLOSED,      Set.of()
    );

    public boolean canTransitionTo(Status next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
