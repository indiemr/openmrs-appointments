package org.openmrs.module.appointments.events.eventListener;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.events.AppointmentBookingEvent;
import org.openmrs.module.appointments.events.AppointmentEventType;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.service.AppointmentBillingService;
import org.openmrs.module.appointments.service.AppointmentCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;


@Component
@Order(1)
public class AppointmentCreateSideEffectsListener {
    
    private final Log log = LogFactory.getLog(getClass());

    @Autowired
    private AppointmentBillingService appointmentBillingService;

    @Autowired
    private AppointmentCalendarService appointmentCalendarService;

    @EventListener
    public void onAppointmentCreated(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_CREATED) {
            return;
        }

        Appointment responseAppointment = event.getAppointment();
        String appointmentUuid = responseAppointment.getUuid();
        boolean createBill = Boolean.TRUE.equals(responseAppointment.getCreateBill());

        // 1) Bill in its own TX
        try {
            String billUuid = appointmentBillingService.createBillForAppointment(appointmentUuid, createBill);
            if (StringUtils.isNotBlank(billUuid)) {
                responseAppointment.setBillUuid(billUuid);
            }
        } catch (Exception e) {
            log.error("Bill creation failed for appointment " + appointmentUuid, e);
        }

        // 2) Calendar/Meet in its own TX
        try {
            String meetingUrl = appointmentCalendarService.createCalendarEventForAppointment(appointmentUuid);
            if (StringUtils.isNotBlank(meetingUrl)) {
                responseAppointment.setTeleHealthVideoLink(meetingUrl);
            }
        } catch (Exception e) {
            log.error("Calendar creation failed for appointment " + appointmentUuid, e);
        }
    }

    @EventListener
    public void onAppointmentUpdated(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_UPDATED) {
            return;
        }

        Appointment responseAppointment = event.getAppointment();
        String appointmentUuid = responseAppointment.getUuid();
        boolean cancelled = AppointmentStatus.Cancelled.equals(responseAppointment.getStatus());
        boolean createBill = Boolean.TRUE.equals(responseAppointment.getCreateBill());

        // 1) create bill on updating appointment (Only if bill doesnt exist)
        if (createBill && !cancelled) {
            try {
                String billUuid = appointmentBillingService.createBillForAppointment(appointmentUuid, createBill);
                if (StringUtils.isNotBlank(billUuid)) {
                    responseAppointment.setBillUuid(billUuid);
                }
            } catch (Exception e) {
                log.error("Bill creation failed for appointment " + appointmentUuid, e);
            }
        }

        // 2) Void bill only on cancel (if bill exists)
        if (cancelled && StringUtils.isNotBlank(responseAppointment.getBillUuid())) {
            try {
                appointmentBillingService.voidBillForAppointment(appointmentUuid, "Appointment cancelled");
            } catch (Exception e) {
                log.error("Bill void failed for appointment " + appointmentUuid, e);
            }
        }

        try {
            if (cancelled) {
                appointmentCalendarService.cancelCalendarEventForAppointment(appointmentUuid);
            } else {
                appointmentCalendarService.updateCalendarEventForAppointment(appointmentUuid);
            }
        } catch (Exception e) {
            log.error("Calendar sync failed for appointment " + appointmentUuid, e);
        }
    }
}
