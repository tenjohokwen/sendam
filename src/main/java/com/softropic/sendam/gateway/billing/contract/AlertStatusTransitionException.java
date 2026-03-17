package com.softropic.sendam.gateway.billing.contract;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when an admin attempts an invalid alert status transition.
 * Valid transitions: OPEN→ACKNOWLEDGED, OPEN→RESOLVED, ACKNOWLEDGED→RESOLVED.
 * RESOLVED is a terminal state — no transitions allowed from it.
 */
public class AlertStatusTransitionException extends ApplicationException {
    public AlertStatusTransitionException(String message, Long alertId, AlertStatus currentStatus) {
        super(message, DeviationAlertError.INVALID_STATUS_TRANSITION);
        logContext.put("alertId", alertId);
        logContext.put("currentStatus", currentStatus);
    }
}
