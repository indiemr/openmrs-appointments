package org.openmrs.module.appointments.events.eventListener;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.events.AppointmentBookingEvent;
import org.openmrs.module.appointments.events.AppointmentEventType;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.service.AppointmentBillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppointmentBillingEventListener {
    
    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    private AppointmentBillingService appointmentBillingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentUpdate(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_UPDATED) {
            return;
        }
        Appointment appointment = event.getAppointment();
        
        if (StringUtils.isBlank(appointment.getBillUuid())) {
            return; // no bill to void
        }

        if (!AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
            return;
        }

        try {
            appointmentBillingService.voidBillForAppointment(appointment, "Appointment cancelled");
        } catch (Exception e) {
            log.error("Failed to void bill for cancelled appointment " + appointment.getUuid(), e);
        // do NOT rethrow
        }
    }
}
