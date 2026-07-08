package org.openmrs.module.appointments.events.eventListener;

import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.events.AppointmentBookingEvent;
import org.openmrs.module.appointments.events.AppointmentEventType;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.service.AppointmentCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AppointmentCalendarEventListener {

    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    private AppointmentCalendarService appointmentCalendarService;

    @Autowired
    @Qualifier("AppointmentsAsyncThreadExecutor")
    private Executor executor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCreated(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_CREATED) {
            return;
        }

        try {
            appointmentCalendarService.createCalendarEventForAppointment(event.getAppointment());
        } catch (Exception e) {
            log.error("Failed to create calendar for " + event.getAppointment().getUuid(), e);
        }
        // executor.execute(() -> runWithContext(event, () ->
        // appointmentCalendarService.createCalendarEventForAppointment(event.getAppointment())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentUpdated(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_UPDATED) {
            return;
        }
        try {
            Appointment appointment = event.getAppointment();
            if (AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
                appointmentCalendarService.cancelCalendarEventForAppointment(appointment);
            } else {
                appointmentCalendarService.updateCalendarEventForAppointment(appointment);
            }    
        } catch (Exception e) {
            log.error("Failed to update calendar for " + event.getAppointment().getUuid(), e);
        }
        
        // executor.execute(() -> runWithContext(event, () -> {
        //     Appointment appointment = event.getAppointment();
        //     if (AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
        //         appointmentCalendarService.cancelCalendarEventForAppointment(appointment);
        //     } else {
        //         appointmentCalendarService.updateCalendarEventForAppointment(appointment);
        //     }
        // }));
    }

    private void runWithContext(AppointmentBookingEvent event, Runnable action) {
        try {
            Context.openSession();
            Context.setUserContext(event.userContext);
            action.run();
        } catch (Exception e) {
            log.error("Failed to sync calendar for " + event.getAppointment().getUuid(), e);
        } finally {
            Context.closeSession();
        }
    }
}
