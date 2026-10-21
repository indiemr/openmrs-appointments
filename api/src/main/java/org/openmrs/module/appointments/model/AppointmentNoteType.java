package org.openmrs.module.appointments.model;

public enum AppointmentNoteType {
    REASON_FOR_VISIT,
    ADDITIONAL_INFO,
    RESCHEDULE,
    CANCEL;

    /** Notes about the visit itself, as opposed to the reason for a reschedule or cancel. Editable. */
    public boolean isVisitNote() {
        return this == REASON_FOR_VISIT || this == ADDITIONAL_INFO;
    }
}
