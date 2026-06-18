package org.openmrs.module.appointments.events;

import org.openmrs.module.appointments.model.Appointment;

public class AppointmentRescheduledEvent extends AppointmentEvent {
    private final Appointment previousAppointment;
    private final Appointment rescheduledAppointment;

    public AppointmentRescheduledEvent(Appointment previousAppointment, Appointment rescheduledAppointment) {
        super(AppointmentEventType.BAHMNI_APPOINTMENT_RESCHEDULED);
        this.previousAppointment = previousAppointment;
        this.rescheduledAppointment = rescheduledAppointment;
        this.payloadId = rescheduledAppointment.getUuid();
    }

    public Appointment getPreviousAppointment() {
        return previousAppointment;
    }

    public Appointment getRescheduledAppointment() {
        return rescheduledAppointment;
    }
    
}
