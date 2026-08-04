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
public class AppointmentBookingSmsNotifier {
    private static final String APPOINTMENT_SMS_MESSAGE = "hi";

    public void sendBookingSms(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = AppointmentSmsHelper.getPhoneNumber(appointment, "Phone number not found");
        if (phoneNumber == null) {
            return;
        }

        OutgoingSms outgoingSms = buildOutgoingSms(phoneNumber, appointment, appointmentArgumentsMapper);
        AppointmentSmsHelper.sendWithSmsModulePrivilege(outgoingSms, "Failed to send scheduled appointment SMS");
    }

    private OutgoingSms buildOutgoingSms(String phoneNumber, Appointment appointment,
            AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, Object> customParams = new HashMap<>();

        Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);

        String patientName = arguments.get("patientname") != null ? arguments.get("patientname") : "";
        String providerNames = AppointmentSmsHelper.getProviderNames(appointment);
        String appointmentDate = arguments.get("date") != null ? arguments.get("date") : "";
        String appointmentTime = AppointmentSmsHelper.getAppointmentTime12Hour(appointment);
        String locationName = getLocationName(appointment, appointmentArgumentsMapper);

        customParams.put("var1", patientName);
        customParams.put("var2", providerNames);
        customParams.put("var3", appointmentDate);
        customParams.put("var4", appointmentTime);
        customParams.put("var5", locationName);

        String smsConfig = Context.getAdministrationService().getGlobalProperty(
            SmsGlobalPropertyConstants.BOOKING_TEMPLATE_CONFIG,
            SmsGlobalPropertyConstants.DEFAULT_BOOKING_TEMPLATE_CONFIG);
        return new OutgoingSms(smsConfig, phoneNumber, APPOINTMENT_SMS_MESSAGE, customParams);
    }

    private String getLocationName(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        Map<String, String> arguments = appointmentArgumentsMapper.createArgumentsMapForAppointmentBooking(appointment);
        String facilityName = arguments.get("facilityname");
        return facilityName != null ? facilityName : "";
    }
}
