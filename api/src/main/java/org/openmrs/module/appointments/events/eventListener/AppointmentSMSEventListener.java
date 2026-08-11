package org.openmrs.module.appointments.events.eventListener;

import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.events.AppointmentEventType;
import org.openmrs.module.appointments.events.AppointmentBookingEvent;
import org.openmrs.module.appointments.events.AppointmentEvent;
import org.openmrs.module.appointments.events.AppointmentRescheduledEvent;
import org.openmrs.module.appointments.events.RecurringAppointmentEvent;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentKind;
import org.openmrs.module.appointments.notification.AppointmentBookingSmsNotifier;
import org.openmrs.module.appointments.notification.AppointmentRescheduleSmsNotifier;
import org.openmrs.module.appointments.notification.AppointmentTeleconsultationSmsNotifier;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.openmrs.module.appointments.util.AppointmentStatusUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class AppointmentSMSEventListener {

    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    private AppointmentArgumentsMapper appointmentArgumentsMapper;

    @Autowired
    private AppointmentBookingSmsNotifier appointmentBookingSmsNotifier;

    @Autowired
    private AppointmentRescheduleSmsNotifier appointmentRescheduleSmsNotifier;

    @Autowired
    private AppointmentTeleconsultationSmsNotifier appointmentTeleconsultationSmsNotifier;

    @Autowired
    private AppointmentsService appointmentsService;

    @Autowired
    @Qualifier("AppointmentsAsyncThreadExecutor")
    private Executor executor;

    @EventListener
    public void onApplicationEvent(AppointmentBookingEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_CREATED && event.eventType != AppointmentEventType.BAHMNI_APPOINTMENT_UPDATED) {
            return;
        }

        final String appointmentUuid = event.getAppointment().getUuid();
        final Boolean sendSms = event.getAppointment().getSendSms();
        final AppointmentEventType eventType = event.eventType;

        executor.execute(() -> runWithContext(event, () -> {
            Appointment appointment = appointmentsService.getAppointmentByUuid(appointmentUuid);
            if (appointment == null) {
                log.error("Appointment not found for sms " + appointmentUuid);
                return;
            }
            appointment.setSendSms(sendSms);
            if (eventType == AppointmentEventType.BAHMNI_APPOINTMENT_CREATED) {
                handleAppointmentCreatedEvent(appointment);
            } else {
                handleAppointmentUpdatedEvent(appointment);
            }
        }));
    }

    @EventListener
    public void onApplicationEvent(RecurringAppointmentEvent event) {
        if (event.eventType != AppointmentEventType.BAHMNI_RECURRING_APPOINTMENT_CREATED) {
            return;
        }

        Appointment firstAppointment = event.getAppointmentRecurringPattern().getAppointments().iterator().next();
        final String appointmentUuid = firstAppointment.getUuid();
        final Boolean sendSms = firstAppointment.getSendSms();

        executor.execute(() -> runWithContext(event, () -> {
            Appointment appointment = appointmentsService.getAppointmentByUuid(appointmentUuid);
            if (appointment == null) {
                log.error("Appointment not found for recurring SMS: " + appointmentUuid);
                return;
            }
            appointment.setSendSms(sendSms);
            handleRecurringAppointmentCreatedEvent(appointment);
        }));
    }

    @EventListener
    public void onApplicationEvent(AppointmentRescheduledEvent event) {
        
        final String previousUuid = event.getPreviousAppointment().getUuid();
        final String rescheduledUuid = event.getRescheduledAppointment().getUuid();
        final Boolean sendSms = event.getRescheduledAppointment().getSendSms();

        executor.execute(() -> runWithContext(event, () -> {
            Appointment previous = appointmentsService.getAppointmentByUuid(previousUuid);
            Appointment rescheduled = appointmentsService.getAppointmentByUuid(rescheduledUuid);
            if (previous == null || rescheduled == null) {
                log.error("Appointment(s) not found for reschedule SMS " + previousUuid + "/" + rescheduledUuid);
                return;
            }
            rescheduled.setSendSms(sendSms);
            handleAppointmentRescheduledEvent(previous, rescheduled);
        }));
    }

    private void runWithContext(AppointmentEvent event, Runnable action) {
        try {
            Context.openSession();
            Context.setUserContext(event.userContext);
            action.run();
        } catch (Exception e) {
            log.error("Exception occurred during SMS event processing", e);
        } finally {
            Context.closeSession();
        }
    }

    private void handleAppointmentCreatedEvent(Appointment appointment) {
        // Tele consultation appointment sms
        if (isVirtualAppointment(appointment)) {
            if (!shouldSendTeleconsultationSms(appointment)) {
                return;
            }
            appointmentTeleconsultationSmsNotifier.sendTeleconsultationSms(appointment, appointmentArgumentsMapper);
            return;
        }

        // Scheduled appointment sms
        if (!shouldSendBookingSms(appointment)) {
            return;
        }
        appointmentBookingSmsNotifier.sendBookingSms(appointment, appointmentArgumentsMapper);
    }

    private void handleAppointmentUpdatedEvent(Appointment appointment) {
        if (isVirtualAppointment(appointment)) {
            if (!shouldSendTeleconsultationSms(appointment)) {
                return;
            }
            appointmentTeleconsultationSmsNotifier.sendTeleconsultationSms(appointment, appointmentArgumentsMapper);
            return;
        }
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
        if (!isConfirmedAppointment(appointment)) {
            return false;
        }
        if (Boolean.FALSE.equals(appointment.getSendSms())) {
            log.info("Skipping booking SMS: sendSms=false on appointment request.");
            return false;
        }
        return isGlobalBookingSmsEnabled();
    }

    private boolean shouldSendReschedulingSms(Appointment appointment) {
        if (!isConfirmedAppointment(appointment)) {
            return false;
        }
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

    private boolean isVirtualAppointment(Appointment appointment) {
        return appointment.getAppointmentKind() != null && appointment.getAppointmentKind() == AppointmentKind.Virtual;
    }

    private boolean shouldSendTeleconsultationSms(Appointment appointment) {
        if (!isConfirmedAppointment(appointment)) {
            return false;
        }
        if (Boolean.FALSE.equals(appointment.getSendSms())) {
            log.info("Skipping teleconsultation SMS: sendSms=false on appointment request.");
           return false;
        }
        AdministrationService administrationService = Context.getService(AdministrationService.class);
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            return Boolean.parseBoolean(
                    administrationService.getGlobalProperty("sms.enableTeleconsultationSMSAlert", "false"));
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }
    }

    private boolean isConfirmedAppointment(Appointment appointment) {
        if (appointment == null || !AppointmentStatusUtil.isConfirmed(appointment.getStatus())) {
            log.info("Skipping SMS: appointment status is not Confirmed for "
                    + (appointment != null ? appointment.getUuid() : null)
                    + " status=" + (appointment != null ? appointment.getStatus() : null));
            return false;
        }
        return true;
    }
}
