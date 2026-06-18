package org.openmrs.module.appointments.notification;

import static org.openmrs.module.appointments.util.DateUtil.convertUTCToGivenFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.sms.api.service.OutgoingSms;
import org.openmrs.module.sms.api.service.SmsService;
import org.openmrs.module.sms.api.util.PrivilegeConstants;
import org.springframework.stereotype.Component;

@Component
public class AppointmentReminderSmsNotifier {
    private static final String APPOINTMENT_REMINDER_SMS_CONFIG = "AppointmentReminder";
    private static final String APPOINTMENT_REMINDER_SMS_MESSAGE = "reminder";
    private static final String PERSON_ATTRIBUTE_TYPE_PHONE_NUMBER = "phoneNumber";

    private final Log log = LogFactory.getLog(this.getClass());

    public void sendReminderSms(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = getPhoneNumber(appointment);
        if (phoneNumber == null) {
            return;
        }
        OutgoingSms outgoingSms = buildOutgoingSms(phoneNumber, appointment, appointmentArgumentsMapper);
        sendWithSmsModulePrivilege(outgoingSms);
    }

    private OutgoingSms buildOutgoingSms(String phoneNumber, Appointment appointment,
            AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = new HashMap<>();
        String providerNames = getProviderNames(
                appointmentArgumentsMapper.getProvidersNameInString(appointment));
        customParams.put("var1", formatConsultationWithProvider(providerNames));
        String locationName = getLocationName(appointment, appointmentArgumentsMapper);
        String timing = getAppointmentTime12Hour(appointment);
        customParams.put("var2", formatLocationAtTiming(locationName, timing));
        // Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);
        // customParams.put("var3", arguments.get("date") != null ? arguments.get("date") : "");
        return new OutgoingSms(APPOINTMENT_REMINDER_SMS_CONFIG, phoneNumber, APPOINTMENT_REMINDER_SMS_MESSAGE,
                customParams);
    }

    private String getProviderNames(List<String> providerNames) {
        if (providerNames == null || providerNames.isEmpty()) {
            return "";
        }
        return providerNames.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(", "));
    }

    private String getLocationName(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);
        String facilityName = arguments.get("facilityname");
        return facilityName != null ? facilityName : "";
    }

    private String getPhoneNumber(Appointment appointment) {
        if (appointment.getPatient() == null
                || appointment.getPatient().getAttribute(PERSON_ATTRIBUTE_TYPE_PHONE_NUMBER) == null) {
            log.info("No mobile number found for the patient. Reminder SMS not sent.");
            return null;
        }

        return appointment.getPatient().getAttribute(PERSON_ATTRIBUTE_TYPE_PHONE_NUMBER).getValue();
    }

    private String formatConsultationWithProvider(String providerNames) {
        if (StringUtils.isBlank(providerNames)) {
            return "consultation";
        }
        return "consultation with " + providerNames;
    }

    private String formatLocationAtTiming(String locationName, String timing) {
        if (StringUtils.isBlank(locationName)) {
            return StringUtils.isNotBlank(timing) ? timing : "";
        }
        if (StringUtils.isBlank(timing)) {
            return locationName;
        }
        return locationName + " at " + timing;
    }

    private String getAppointmentTime12Hour(Appointment appointment) {
        if (appointment.getStartDateTime() == null) {
            return "";
        }
        String timeZone = Context.getAdministrationService().getGlobalProperty("sms.timezone", "IST");
        String formatted = convertUTCToGivenFormat(appointment.getStartDateTime(), "hh:mm a", timeZone);
        return formatted != null ? formatted : "";
    }

    private void sendWithSmsModulePrivilege(OutgoingSms outgoingSms) {
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
            Context.getService(SmsService.class).send(outgoingSms);
        } catch (Exception e) {
            log.error("Failed to send appointment reminder SMS", e);
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
        }
    }

}