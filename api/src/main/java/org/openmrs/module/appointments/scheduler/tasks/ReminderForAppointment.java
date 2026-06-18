package org.openmrs.module.appointments.scheduler.tasks;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bahmni.module.communication.service.CommunicationService;
import org.bahmni.module.communication.service.MessageBuilderService;
import org.openmrs.PersonAttribute;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.notification.AppointmentReminderSmsNotifier;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.openmrs.scheduler.tasks.AbstractTask;

import java.util.List;

public class ReminderForAppointment extends AbstractTask {
    private Log log = LogFactory.getLog(this.getClass());

    @Override
    public void execute() {
        log.info("Appointment reminder scheduler task started");
        AdministrationService administrationService = Context.getService(AdministrationService.class);

        boolean scheduleSMS = Boolean.parseBoolean(administrationService.getGlobalProperty("sms.enableAppointmentReminderSMSAlert", "false"));

        if (!scheduleSMS) {
            log.info("Appointment reminder SMS is disabled (sms.enableAppointmentReminderSMSAlert=false). Skipping.");
            return;
        }

        AppointmentsService appointmentsService = Context.getService(AppointmentsService.class);
        String schedulerReminderTime = administrationService.getGlobalPropertyObject("SchedulerReminderBeforeHours").getPropertyValue();
        log.info("SchedulerReminderBeforeHours=" + schedulerReminderTime);

        List<Appointment> appointments = appointmentsService.getAllAppointmentsReminder(schedulerReminderTime);
        AppointmentArgumentsMapper appointmentArgumentsMapper = Context.getService(AppointmentArgumentsMapper.class);

        log.info("Found " + appointments.size() + " appointment(s) eligible for reminder SMS");


        AppointmentReminderSmsNotifier reminderSmsNotifier = Context.getRegisteredComponents(AppointmentReminderSmsNotifier.class).get(0);
        for (Appointment appointment : appointments) {
            try {
                log.info("Processing reminder for appointment uuid=" + appointment.getUuid()
                + ", startDateTime=" + appointment.getStartDateTime()
                + ", location=" + (appointment.getLocation() != null
                        ? appointment.getLocation().getName() + " (" + appointment.getLocation().getUuid() + ")"
                        : "null"));
                reminderSmsNotifier.sendReminderSms(appointment, appointmentArgumentsMapper);
            } catch (Exception e) {
                log.error("Failed to send reminder SMS for appointment: " + appointment.getUuid(), e);
            }
        }
        // for (Appointment appointment: appointments) {
        //     PersonAttribute phoneNumber = appointment.getPatient().getAttribute("phoneNumber");
        //     if (null == phoneNumber) {
        //         log.info("Since no mobile number found for the patient. SMS not sent.");
        //         return;
        //     }
        //     MessageBuilderService smsBuilderService =Context.getService(MessageBuilderService.class);
        //     AppointmentArgumentsMapper appointmentArgumentsMapper=Context.getService(AppointmentArgumentsMapper.class);
        //     String message = smsBuilderService.getAppointmentReminderMessage(appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment),appointmentArgumentsMapper.getProvidersNameInString(appointment));
        //     CommunicationService communicationService=Context.getService(CommunicationService.class);
        //     communicationService.sendSMS(phoneNumber.getValue(), message);
        // }
    }
}
