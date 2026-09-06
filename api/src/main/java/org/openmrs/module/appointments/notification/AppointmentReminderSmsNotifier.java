package org.openmrs.module.appointments.notification;
import java.util.HashMap;
import java.util.Map;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.constants.SmsGlobalPropertyConstants;
import org.openmrs.module.appointments.helper.AppointmentSmsHelper;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.sms.api.service.OutgoingSms;
import org.springframework.stereotype.Component;

@Component
public class AppointmentReminderSmsNotifier {
    private static final String APPOINTMENT_REMINDER_SMS_MESSAGE = "reminder";

    public boolean sendReminderSms(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = AppointmentSmsHelper.getPhoneNumber(appointment, "Phone number not found");
        if (phoneNumber == null) {
            return false;
        }
        OutgoingSms outgoingSms = buildOutgoingSms(phoneNumber, appointment, appointmentArgumentsMapper);
        AppointmentSmsHelper.sendWithSmsModulePrivilege(outgoingSms, "Failed to send appointment reminder SMS");
        return true;
    }

    private OutgoingSms buildOutgoingSms(String phoneNumber, Appointment appointment,
            AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = buildCustomParams(appointment, appointmentArgumentsMapper);
        String smsConfig = Context.getAdministrationService().getGlobalProperty(
            SmsGlobalPropertyConstants.REMINDER_TEMPLATE_CONFIG,
            SmsGlobalPropertyConstants.DEFAULT_REMINDER_TEMPLATE_CONFIG);
        return new OutgoingSms(smsConfig, phoneNumber, APPOINTMENT_REMINDER_SMS_MESSAGE,
                customParams);
    }

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