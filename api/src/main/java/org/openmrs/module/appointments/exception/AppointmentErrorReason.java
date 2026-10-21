package org.openmrs.module.appointments.exception;

/**
 * Why an appointment or hold operation was rejected. Callers branch on the reason, not on the
 * message text. Reasons with a message are produced by validators, which only collect strings;
 * {@link #fromMessage(String)} maps those strings back to the reason.
 */
public enum AppointmentErrorReason {

    INVALID_REQUEST(null),
    SLOT_LOCK_BUSY(null),
    ACTIVE_HOLD_LIMIT(null),
    UNUSED_HOLD_LIMIT(null),
    SLOT_FULL("Selected time slot is full. Please choose another slot."),
    SLOT_UNAVAILABLE("Selected time slot is unavailable. Please choose another slot."),
    HOLD_EXPIRED(null),
    HOLD_RELEASED(null),
    HOLD_ALREADY_USED(null),
    HOLD_MAX_TIME_REACHED(null),
    OUTSIDE_BOOKING_WINDOW("Selected date is outside the allowed booking window."),
    LEAD_TIME_NOT_MET("Appointment must be booked in advance according to the minimum notice period."),
    CANCELLATION_CUTOFF_PASSED("This appointment can no longer be cancelled because the cancellation cutoff has passed."),
    // Validator appends "<from> to <to>"
    STATUS_CHANGE_NOT_ALLOWED("Appointment status can not be changed from"),
    PROVIDER_REQUIRED("Provider is required to create a virtual appointment"),
    PROVIDER_CALENDAR_NOT_CONNECTED("Google account is required to create tele consultation"),
    NOTE_REQUIRED(null),
    NOTE_TOO_LONG(null),
    NOTE_NOT_FOUND(null),
    NOTE_NOT_EDITABLE(null),
    NOTE_TYPE_NOT_ALLOWED(null),
    TOO_MANY_NOTES(null);

    private final String message;

    AppointmentErrorReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public static AppointmentErrorReason fromMessage(String error) {
        if (error == null) {
            return null;
        }
        for (AppointmentErrorReason reason : values()) {
            if (reason.message != null && error.startsWith(reason.message)) {
                return reason;
            }
        }
        return null;
    }
}
