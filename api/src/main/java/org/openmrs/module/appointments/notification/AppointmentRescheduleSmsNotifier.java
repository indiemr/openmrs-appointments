package org.openmrs.module.appointments.notification;

import static org.openmrs.module.appointments.util.DateUtil.convertUTCToGivenFormat;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.constants.SmsGlobalPropertyConstants;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.sms.api.service.OutgoingSms;
import org.openmrs.module.sms.api.service.SmsService;
import org.openmrs.module.sms.api.util.PrivilegeConstants;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AppointmentRescheduleSmsNotifier {
    private static final String APPOINTMENT_RESCHEDULE_SMS_MESSAGE = "reschedule";

    private final Log log = LogFactory.getLog(this.getClass());

    // ---------------------------- RESCHEDULE METHODS  -------------------------------

    public void sendRescheduleSms(Appointment previousAppointment, Appointment rescheduledAppointment,
            AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = getPhoneNumber(rescheduledAppointment);
        if (phoneNumber == null) {
            return;
        }

        OutgoingSms outgoingSms = buildOutgoingSms(phoneNumber, previousAppointment, rescheduledAppointment,
                appointmentArgumentsMapper);
        sendWithSmsModulePrivilege(outgoingSms);
    }

    private OutgoingSms buildOutgoingSms(String phoneNumber, Appointment previousAppointment,
            Appointment rescheduledAppointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = buildCustomParams(rescheduledAppointment, appointmentArgumentsMapper);
        String smsConfig = Context.getAdministrationService().getGlobalProperty(
            SmsGlobalPropertyConstants.RESCHEDULE_TEMPLATE_CONFIG,
            SmsGlobalPropertyConstants.DEFAULT_RESCHEDULE_TEMPLATE_CONFIG);

        return new OutgoingSms(smsConfig, phoneNumber, APPOINTMENT_RESCHEDULE_SMS_MESSAGE,
                customParams);
    }

    // ---------------------------- UPDATE APPOINTMENT METHODS -------------------------------

    public void sendUpdateSms(Appointment updatedAppointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = getPhoneNumber(updatedAppointment);
        if (phoneNumber == null) {
            return;
        }
        OutgoingSms outgoingSms = buildOutgoingSmsForUpdate(phoneNumber, updatedAppointment,
                appointmentArgumentsMapper);
        sendWithSmsModulePrivilege(outgoingSms);
    }

    private OutgoingSms buildOutgoingSmsForUpdate(String phoneNumber, Appointment updatedAppointment,
            AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = buildCustomParams(updatedAppointment, appointmentArgumentsMapper);
        String smsConfig = Context.getAdministrationService().getGlobalProperty(
            SmsGlobalPropertyConstants.UPDATE_TEMPLATE_CONFIG,
            SmsGlobalPropertyConstants.DEFAULT_UPDATE_TEMPLATE_CONFIG);

        return new OutgoingSms(smsConfig, phoneNumber, APPOINTMENT_RESCHEDULE_SMS_MESSAGE,
                customParams);
    }

    // ---------------------------- GETTER METHODS -------------------------------

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

    private String getPhoneNumber(Appointment appointment) {
        if (appointment.getPatient() == null) {
            log.info("No mobile number found for the patient. Reschedule SMS not sent.");
            return null;
        }
        Patient patient = Context.getPatientService().getPatientByUuid(appointment.getPatient().getUuid());
        if (patient == null || patient.getAttribute("phoneNumber") == null) {
            log.info("No mobile number found for the patient. Reschedule SMS not sent.");
            return null;
        }
        return patient.getAttribute("phoneNumber").getValue();
    }

    // ---------------------------- SMS METHODS -------------------------------

    private void sendWithSmsModulePrivilege(OutgoingSms outgoingSms) {
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
            Context.getService(SmsService.class).send(outgoingSms);
        } catch (Exception e) {
            log.error("Failed to send appointment reschedule SMS", e);
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
        }
    }

    private String getAppointmentTime12Hour(Appointment appointment) {
        if (appointment.getStartDateTime() == null) {
            return "";
        }
        String timeZone = Context.getAdministrationService().getGlobalProperty("sms.timezone", "IST");
        String formatted = convertUTCToGivenFormat(appointment.getStartDateTime(), "hh:mm a", timeZone);
        return formatted != null ? formatted : "";
    }

    // ---------------------------- PAYLOAD PREPARATION -------------------------------
    private Map<String, Object> buildCustomParams(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = new HashMap<>();
        Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);

        String patientName = arguments.get("patientname") != null ? arguments.get("patientname") : "";
        String providerNames = getProviderNames(appointmentArgumentsMapper.getProvidersNameInString(appointment));
        String appointmentDate = arguments.get("date") != null ? arguments.get("date") : "";
        String appointmentTime = getAppointmentTime12Hour(appointment);
        String locationName = arguments.get("facilityname") != null ? arguments.get("facilityname") : "";
        customParams.put("var1", patientName);
        customParams.put("var2", providerNames);
        customParams.put("var3", appointmentDate);
        customParams.put("var4", appointmentTime);
        customParams.put("var5", locationName);
        return customParams;
    }
}
