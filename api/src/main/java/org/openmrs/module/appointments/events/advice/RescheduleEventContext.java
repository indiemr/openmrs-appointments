package org.openmrs.module.appointments.events.advice;

import org.openmrs.module.appointments.model.Appointment;

public final class RescheduleEventContext {

    private static final ThreadLocal<Appointment> PREVIOUS_APPOINTMENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RESCHEDULE_IN_PROGRESS = new ThreadLocal<>();

    private RescheduleEventContext() {
    }

    public static void begin(Appointment previousAppointment) {
        PREVIOUS_APPOINTMENT.set(previousAppointment);
        RESCHEDULE_IN_PROGRESS.set(Boolean.TRUE);
    }

    public static boolean isRescheduleInProgress() {
        return Boolean.TRUE.equals(RESCHEDULE_IN_PROGRESS.get());
    }

    public static Appointment getPreviousAppointment() {
        return PREVIOUS_APPOINTMENT.get();
    }

    public static void clear() {
        PREVIOUS_APPOINTMENT.remove();
        RESCHEDULE_IN_PROGRESS.remove();
    }
}
