package com.softropic.sendam.gateway.billing.contract;

public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED;

    /**
     * Valid transitions:
     * OPEN         → ACKNOWLEDGED or RESOLVED
     * ACKNOWLEDGED → RESOLVED
     * RESOLVED     → nothing (terminal state)
     */
    public boolean canTransitionTo(AlertStatus target) {
        return switch (this) {
            case OPEN         -> target == ACKNOWLEDGED || target == RESOLVED;
            case ACKNOWLEDGED -> target == RESOLVED;
            case RESOLVED     -> false;
        };
    }
}
