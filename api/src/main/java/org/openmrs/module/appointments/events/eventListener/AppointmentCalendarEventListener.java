package org.openmrs.module.appointments.events.eventListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.events.AppointmentBookingEvent;
import org.openmrs.module.appointments.events.AppointmentEventType;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.service.AppointmentCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AppointmentCalendarEventListener {

    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    private AppointmentCalendarService appointmentCalendarService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCreated(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_CREATED) {
            return;
        }
        try {
            appointmentCalendarService.createCalendarEventForAppointment(event.getAppointment());
        } catch (Exception e) {
            log.error("Failed to sync calendar event on appointment create " + event.getAppointment().getUuid(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentUpdated(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_UPDATED) {
            return;
        }

        Appointment appointment = event.getAppointment();
        try {
            if (AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
                appointmentCalendarService.cancelCalendarEventForAppointment(appointment);
            } else {
                appointmentCalendarService.updateCalendarEventForAppointment(appointment);
            }
        } catch (Exception e) {
            log.error("Failed to sync calendar event on appointment update " + appointment.getUuid(), e);
        }
    }
}
