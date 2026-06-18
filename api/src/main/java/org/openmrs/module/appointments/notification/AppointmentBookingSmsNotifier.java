package org.openmrs.module.appointments.notification;

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

import static org.openmrs.module.appointments.util.DateUtil.convertUTCToGivenFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AppointmentBookingSmsNotifier {

    private static final String APPOINTMENT_SMS_CONFIG = "Appointment";
    private static final String APPOINTMENT_SMS_MESSAGE = "hi";

    private final Log log = LogFactory.getLog(this.getClass());

    public void sendBookingSms(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
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

        return new OutgoingSms(APPOINTMENT_SMS_CONFIG, phoneNumber, APPOINTMENT_SMS_MESSAGE, customParams);
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
        if (appointment.getPatient() == null || appointment.getPatient().getAttribute("phoneNumber") == null) {
            log.info("No mobile number found for the patient. SMS not sent.");
            return null;
        }
        return appointment.getPatient().getAttribute("phoneNumber").getValue();
    }

    private void sendWithSmsModulePrivilege(OutgoingSms outgoingSms) {
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
            Context.getService(SmsService.class).send(outgoingSms);
        } catch (Exception e) {
            log.error("Failed to send appointment booking SMS", e);
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
        }
    }

    // ---------------------------------------- DATE UTILS ----------------------------------------
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
}
