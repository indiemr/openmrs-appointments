package org.openmrs.module.appointments.notification;

import static org.openmrs.module.appointments.util.DateUtil.convertUTCToGivenFormat;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
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

    private static final String APPOINTMENT_RESCHEDULE_SMS_CONFIG = "AppointmentReschedule";
    private static final String APPOINTMENT_UPDATE_SMS_CONFIG = "AppointmentUpdate";
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
        Map<String, Object> customParams = new HashMap<>();
        customParams.put("var1", getProviderNames(
                appointmentArgumentsMapper.getProvidersNameInString(rescheduledAppointment)));
        customParams.put("var2", getLocationName(rescheduledAppointment, appointmentArgumentsMapper));
        // customParams.put("var3", getAppointmentDate(previousAppointment, appointmentArgumentsMapper));
        // customParams.put("var4", getAppointmentDate(rescheduledAppointment, appointmentArgumentsMapper));

        return new OutgoingSms(APPOINTMENT_RESCHEDULE_SMS_CONFIG, phoneNumber, APPOINTMENT_RESCHEDULE_SMS_MESSAGE,
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
        Map<String, Object> customParams = new HashMap<>();

        String providerNames = getProviderNames(
                appointmentArgumentsMapper.getProvidersNameInString(updatedAppointment));
        customParams.put("var1", formatConsultationWithProvider(providerNames));

        String locationName = getLocationName(updatedAppointment, appointmentArgumentsMapper);
        String timing = getAppointmentTime12Hour(updatedAppointment);
        customParams.put("var2", formatLocationAtTiming(locationName, timing));

        return new OutgoingSms(APPOINTMENT_UPDATE_SMS_CONFIG, phoneNumber, APPOINTMENT_RESCHEDULE_SMS_MESSAGE,
                customParams);
    }

    // ---------------------------- GETTER METHODS -------------------------------

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

    private String getAppointmentDate(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);
        String date = arguments.get("date");
        return date != null ? date : "";
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

    // ---------------------------- DATETIME UTILS -------------------------------
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
