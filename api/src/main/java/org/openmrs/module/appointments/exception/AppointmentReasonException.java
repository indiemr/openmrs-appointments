package org.openmrs.module.appointments.exception;

import org.openmrs.api.APIException;

/** {@link APIException} that carries a stable {@link AppointmentErrorReason}. */
public class AppointmentReasonException extends APIException {

    private static final long serialVersionUID = 1L;

    private final AppointmentErrorReason reason;

    public AppointmentReasonException(AppointmentErrorReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AppointmentErrorReason getReason() {
        return reason;
    }
}
