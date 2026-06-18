package org.openmrs.module.appointments.events.eventListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.events.AppointmentEventType;
import org.openmrs.module.appointments.events.AppointmentBookingEvent;
import org.openmrs.module.appointments.events.AppointmentRescheduledEvent;
import org.openmrs.module.appointments.events.RecurringAppointmentEvent;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.notification.AppointmentBookingSmsNotifier;
import org.openmrs.module.appointments.notification.AppointmentRescheduleSmsNotifier;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AppointmentSMSEventListener {

    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    private AppointmentArgumentsMapper appointmentArgumentsMapper;

    @Autowired
    private AppointmentBookingSmsNotifier appointmentBookingSmsNotifier;

    @Autowired
    private AppointmentRescheduleSmsNotifier appointmentRescheduleSmsNotifier;

    @Async("AppointmentsAsyncThreadExecutor")
    @EventListener
    public void onApplicationEvent(AppointmentBookingEvent event) {
        try {
            Context.openSession();
            Context.setUserContext(event.userContext);
            if (event.eventType == AppointmentEventType.BAHMNI_APPOINTMENT_CREATED) {
                handleAppointmentCreatedEvent(event.getAppointment());
            } else if (event.eventType == AppointmentEventType.BAHMNI_APPOINTMENT_UPDATED) {
                handleAppointmentUpdatedEvent(event.getAppointment());
            }
        } catch (Exception e) {
            log.error("Exception occurred during event processing", e);
        } finally {
            Context.closeSession();
        }
    }

    @Async("AppointmentsAsyncThreadExecutor")
    @EventListener
    public void onApplicationEvent(RecurringAppointmentEvent event) {
        try {
            Context.openSession();
            Context.setUserContext(event.userContext);
            if (event.eventType == AppointmentEventType.BAHMNI_RECURRING_APPOINTMENT_CREATED) {
                handleRecurringAppointmentCreatedEvent(
                        event.getAppointmentRecurringPattern().getAppointments().iterator().next());
            }
        } catch (Exception e) {
            log.error("Exception occurred during event processing", e);
        } finally {
            Context.closeSession();
        }
    }

    @Async("AppointmentsAsyncThreadExecutor")
    @EventListener
    public void onApplicationEvent(AppointmentRescheduledEvent event) {
        try {
            Context.openSession();
            Context.setUserContext(event.userContext);
            handleAppointmentRescheduledEvent(event.getPreviousAppointment(), event.getRescheduledAppointment());
        } catch (Exception e) {
            log.error("Exception occurred during reschedule SMS event processing", e);
        } finally {
            Context.closeSession();
        }
    }

    private void handleAppointmentCreatedEvent(Appointment appointment) {
        if (!shouldSendBookingSms(appointment)) {
            return;
        }
        appointmentBookingSmsNotifier.sendBookingSms(appointment, appointmentArgumentsMapper);
    }

    private void handleAppointmentUpdatedEvent(Appointment appointment) {
        if (!shouldSendReschedulingSms(appointment)) {
            return;
        }
        appointmentRescheduleSmsNotifier.sendUpdateSms(appointment, appointmentArgumentsMapper);
        
    }

    private void handleRecurringAppointmentCreatedEvent(Appointment appointment) {
        if (!shouldSendBookingSms(appointment)) {
            return;
        }
        appointmentBookingSmsNotifier.sendBookingSms(appointment, appointmentArgumentsMapper);
    }

    private void handleAppointmentRescheduledEvent(Appointment previousAppointment, Appointment rescheduledAppointment) {
        if (!shouldSendReschedulingSms(rescheduledAppointment)) {
            return;
        }
        appointmentRescheduleSmsNotifier.sendRescheduleSms(previousAppointment, rescheduledAppointment,
                appointmentArgumentsMapper);
    }

    private boolean shouldSendBookingSms(Appointment appointment) {
        if (Boolean.FALSE.equals(appointment.getSendSms())) {
            log.info("Skipping booking SMS: sendSms=false on appointment request.");
            return false;
        }
        return isGlobalBookingSmsEnabled();
    }

    private boolean shouldSendReschedulingSms(Appointment appointment) {
        if (Boolean.FALSE.equals(appointment.getSendSms())) {
            log.info("Skipping reschedule SMS: sendSms=false on appointment request.");
            return false;
        }
        return isGlobalReschedulingSmsEnabled();
    }

    private boolean isGlobalBookingSmsEnabled() {
        AdministrationService administrationService = Context.getService(AdministrationService.class);
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            return Boolean.parseBoolean(
                administrationService.getGlobalProperty("sms.enableAppointmentBookingSMSAlert", "false"));
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }
    }

    private boolean isGlobalReschedulingSmsEnabled() {
        AdministrationService administrationService = Context.getService(AdministrationService.class);
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            return Boolean.parseBoolean(
                    administrationService.getGlobalProperty("sms.enableAppointmentReschedulingSMSAlert", "false"));
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }
    }
}
