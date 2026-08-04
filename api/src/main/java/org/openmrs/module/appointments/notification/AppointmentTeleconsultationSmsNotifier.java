package org.openmrs.module.appointments.notification;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.constants.SmsGlobalPropertyConstants;
import org.openmrs.module.appointments.helper.AppointmentSmsHelper;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.sms.api.service.OutgoingSms;
import org.springframework.stereotype.Component;

@Component
public class AppointmentTeleconsultationSmsNotifier {
    private static final String TELE_SMS_MESSAGE = "tele";


    public void sendTeleconsultationSms(Appointment appointment, AppointmentArgumentsMapper appointmentArgumentsMapper) {
        String phoneNumber = AppointmentSmsHelper.getPhoneNumber(appointment, "Phone number not found");
        if (phoneNumber == null) {
            return;
        }
        OutgoingSms outgoingSms = buildOutgoingSms(phoneNumber, appointment, appointmentArgumentsMapper);
        AppointmentSmsHelper.sendWithSmsModulePrivilege(outgoingSms, "Failed to send virtual appointment SMS");
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

        String patientName = AppointmentSmsHelper.nullToEmpty(arguments.get("patientname"));
        String providerNames = AppointmentSmsHelper.getProviderNames(appointment);
        String appointmentDate = AppointmentSmsHelper.nullToEmpty(arguments.get("date"));
        String appointmentTime = AppointmentSmsHelper.getAppointmentTime12Hour(appointment);
        String teleLink = getTeleconsultationLink(appointment, arguments);
        String modifiedTeleLink = teleLink.replace(
            "https://meet.google.com",
            "meet.indiemr.in"
        );
        customParams.put("var1", patientName);
        customParams.put("var2", providerNames);
        customParams.put("var3", appointmentDate);
        customParams.put("var4", appointmentTime);
        customParams.put("var5", modifiedTeleLink);

        return customParams;
    }

    private String getTeleconsultationLink(Appointment appointment, Map<String, String> arguments) {
        if (StringUtils.isNotBlank(appointment.getTeleHealthVideoLink())) {
            return appointment.getTeleHealthVideoLink();
        }
        return AppointmentSmsHelper.nullToEmpty(arguments.get("teleconsultationlink"));
    }
}
