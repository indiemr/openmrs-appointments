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
import org.openmrs.module.appointments.constants.SmsGlobalPropertyConstants;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.sms.api.service.OutgoingSms;
import org.openmrs.module.sms.api.service.SmsService;
import org.openmrs.module.sms.api.util.PrivilegeConstants;
import org.springframework.stereotype.Component;

@Component
public class AppointmentTeleconsultationSmsNotifier {
    private static final String TELE_SMS_MESSAGE = "tele";

    private final Log log = LogFactory.getLog(this.getClass());

    public void sendTeleconsultationSms(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = getPhoneNumber(appointment);
        if (phoneNumber == null) {
            return;
        }
        OutgoingSms outgoingSms = buildOutgoingSms(phoneNumber, appointment, appointmentArgumentsMapper);
        sendWithSmsModulePrivilege(outgoingSms);
    }

    private OutgoingSms buildOutgoingSms(String phoneNumber, Appointment appointment,
        AppointmentArgumentsMapper appointmentArgumentsMapper) {
            Map<String, Object> customParams = buildCustomParams(appointment, appointmentArgumentsMapper);
            String smsConfig = Context.getAdministrationService().getGlobalProperty(
                SmsGlobalPropertyConstants.TELECONSULTATION_TEMPLATE_CONFIG,
                SmsGlobalPropertyConstants.DEFAULT_TELECONSULTATION_TEMPLATE_CONFIG);
            return new OutgoingSms(smsConfig, phoneNumber, TELE_SMS_MESSAGE, customParams);
        }

    private Map<String, Object> buildCustomParams(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = new HashMap<>();
        Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);

        String patientName = nullToEmpty(arguments.get("patientname"));
        String providerNames = getProviderNames(appointmentArgumentsMapper.getProvidersNameInString(appointment));
        String appointmentDate = nullToEmpty(arguments.get("date"));
        String appointmentTime = getAppointmentTime12Hour(appointment);
        String teleLink = getTeleconsultationLink(appointment, arguments);
        customParams.put("var1", patientName);
        customParams.put("var2", providerNames);
        customParams.put("var3", appointmentDate);
        customParams.put("var4", appointmentTime);
        customParams.put("var5", teleLink);

        return customParams;
    }

    private String getTeleconsultationLink(Appointment appointment, Map<String, String> arguments) {
        if (StringUtils.isNotBlank(appointment.getTeleHealthVideoLink())) {
            return appointment.getTeleHealthVideoLink();
        }
        return nullToEmpty(arguments.get("teleconsultationlink"));
    }

    private String getPhoneNumber(Appointment appointment) {
        if (appointment.getPatient() == null
                || appointment.getPatient().getAttribute("phoneNumber") == null) {
            log.info("No mobile number found for the patient. Teleconsultation SMS not sent.");
            return null;
        }
        return appointment.getPatient().getAttribute("phoneNumber").getValue();
    }

    private String getProviderNames(List<String> providerNames) {
        if (providerNames == null || providerNames.isEmpty()) {
            return "";
        }
        return providerNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(this::formatProviderNameWithPrefix)
                .collect(Collectors.joining(", "));
    }
    
    private String formatProviderNameWithPrefix(String name) {
        String trimmed = name.trim();
        if (StringUtils.isBlank(trimmed)) {
            return "";
        }
        if (trimmed.matches("(?i)^(dr\\.?|doctor)\\s+.*")) {
            return trimmed;
        }
        return "Dr. " + trimmed;
    }
    
    private String getAppointmentTime12Hour(Appointment appointment) {
        if (appointment.getStartDateTime() == null) {
            return "";
        }
        String timeZone = Context.getAdministrationService().getGlobalProperty("sms.timezone", "IST");
        String formatted = convertUTCToGivenFormat(appointment.getStartDateTime(), "hh:mm a", timeZone);
        return formatted != null ? formatted : "";
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private void sendWithSmsModulePrivilege(OutgoingSms outgoingSms) {
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
            Context.getService(SmsService.class).send(outgoingSms);
        } catch (Exception e) {
            log.error("Failed to send teleconsultation SMS", e);
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
        }
    }
}
