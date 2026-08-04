package org.openmrs.module.appointments.notification;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.constants.SmsGlobalPropertyConstants;
import org.openmrs.module.appointments.helper.AppointmentSmsHelper;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.sms.api.service.OutgoingSms;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AppointmentRescheduleSmsNotifier {
    private static final String APPOINTMENT_RESCHEDULE_SMS_MESSAGE = "reschedule";

    // ---------------------------- RESCHEDULE METHODS  -------------------------------

    public void sendRescheduleSms(Appointment previousAppointment, Appointment rescheduledAppointment,
            AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = AppointmentSmsHelper.getPhoneNumber(rescheduledAppointment, "Phone number not found");
        if (phoneNumber == null) {
            return;
        }

        OutgoingSms outgoingSms = buildOutgoingSms(phoneNumber, previousAppointment, rescheduledAppointment,
                appointmentArgumentsMapper);
        AppointmentSmsHelper.sendWithSmsModulePrivilege(outgoingSms, "Failed to send reschedule appointment SMS");
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
        String phoneNumber = AppointmentSmsHelper.getPhoneNumber(updatedAppointment, "Phone number not found");
        if (phoneNumber == null) {
            return;
        }
        OutgoingSms outgoingSms = buildOutgoingSmsForUpdate(phoneNumber, updatedAppointment,
                appointmentArgumentsMapper);
        AppointmentSmsHelper.sendWithSmsModulePrivilege(outgoingSms, "Failed to send reschedule appointment SMS");
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

    // ---------------------------- PAYLOAD PREPARATION -------------------------------
    private Map<String, Object> buildCustomParams(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = new HashMap<>();
        Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);

        String patientName = arguments.get("patientname") != null ? arguments.get("patientname") : "";
        String providerNames = AppointmentSmsHelper.getProviderNames(appointment);
        String appointmentDate = arguments.get("date") != null ? arguments.get("date") : "";
        String appointmentTime = AppointmentSmsHelper.getAppointmentTime12Hour(appointment);
        String locationName = arguments.get("facilityname") != null ? arguments.get("facilityname") : "";
        customParams.put("var1", patientName);
        customParams.put("var2", providerNames);
        customParams.put("var3", appointmentDate);
        customParams.put("var4", appointmentTime);
        customParams.put("var5", locationName);
        return customParams;
    }
}
