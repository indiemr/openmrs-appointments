package org.openmrs.module.appointments.events.eventListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.events.AppointmentBookingEvent;
import org.openmrs.module.appointments.events.AppointmentEventType;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentBillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentBillingEventListener {
    
    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    private AppointmentBillingService appointmentBillingService;

    @EventListener
    public void onApplicationEvent(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_CREATED) {
            return;
        }

        Appointment appointment = event.getAppointment();
        if (!Boolean.TRUE.equals(appointment.getCreateBill())) {
            return;
        }

        try {
            appointmentBillingService.createBillForAppointment(appointment);
        } catch (Exception e) {
            log.error("Failed to create bill for appointment " + appointment.getUuid(), e);
            throw new RuntimeException("Failed to create bill for appointment: " + e.getMessage(), e);
        }
    }
}
