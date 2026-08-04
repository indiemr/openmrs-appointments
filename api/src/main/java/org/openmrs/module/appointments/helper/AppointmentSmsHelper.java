package org.openmrs.module.appointments.helper;

import static org.openmrs.module.appointments.util.DateUtil.convertUTCToGivenFormat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentProviderResponse;
import org.openmrs.module.appointments.service.AppointmentArgumentsMapper;
import org.openmrs.module.sms.api.service.OutgoingSms;
import org.openmrs.module.sms.api.service.SmsService;
import org.openmrs.module.sms.api.util.PrivilegeConstants;

public final class AppointmentSmsHelper {

    private static final String PHONE_NUMBER_ATTR = "phoneNumber";
    private static final Log log = LogFactory.getLog(AppointmentSmsHelper.class);

    private AppointmentSmsHelper() {
    }

    public static String getPhoneNumber(Appointment appointment, String notFoundLogMessage) {
        if (appointment == null || appointment.getPatient() == null) {
            log.info(notFoundLogMessage);
            return null;
        }
        Patient patient = Context.getPatientService().getPatientByUuid(appointment.getPatient().getUuid());
        if (patient == null || patient.getAttribute(PHONE_NUMBER_ATTR) == null) {
            log.info(notFoundLogMessage);
            return null;
        }
        return patient.getAttribute(PHONE_NUMBER_ATTR).getValue();
    }

    public static String getProviderNames(Appointment appointment) {
        if (appointment == null) {
            return "";
        }

        Set<AppointmentProvider> acceptedProviders =
            appointment.getProvidersWithResponse(AppointmentProviderResponse.ACCEPTED);
        
        if (acceptedProviders == null || acceptedProviders.isEmpty()) {
            return "";
        }

        return acceptedProviders.stream()
                .filter(ap -> ap != null && !Boolean.TRUE.equals(ap.getVoided()) && ap.getProvider() != null)
                .map(ap -> formatProviderDisplayName(ap.getProvider()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
    }

    public static String getAppointmentTime12Hour(Appointment appointment) {
        if (appointment == null || appointment.getStartDateTime() == null) {
            return "";
        }
        String timeZone = Context.getAdministrationService().getGlobalProperty("sms.timezone", "IST");
        String formatted = convertUTCToGivenFormat(appointment.getStartDateTime(), "hh:mm a", timeZone);
        return formatted != null ? formatted : "";
    }

    public static void sendWithSmsModulePrivilege(OutgoingSms outgoingSms, String failureLogMessage) {
        try {
            Context.getUserContext().addProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
            Context.getService(SmsService.class).send(outgoingSms);
        } catch (Exception e) {
            log.error(failureLogMessage, e);
        } finally {
            Context.getUserContext().removeProxyPrivilege(PrivilegeConstants.SMS_MODULE_PRIVILEGE);
        }
    }

    /**
     * Shared by booking / reminder / reschedule / update (var5 = facility).
     */
    public static Map<String, Object> buildStandardCustomParams(Appointment appointment,
            AppointmentArgumentsMapper mapper) {
        Map<String, Object> customParams = new HashMap<>();
        Map<String, String> arguments = mapper.createArgumentsMapForAppointmentBooking(appointment);

        customParams.put("var1", nullToEmpty(arguments.get("patientname")));
        customParams.put("var2", getProviderNames(appointment));
        customParams.put("var3", nullToEmpty(arguments.get("date")));
        customParams.put("var4", getAppointmentTime12Hour(appointment));
        customParams.put("var5", nullToEmpty(arguments.get("facilityname")));

        return customParams;
    }

    public static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String formatProviderDisplayName(Provider provider) {
        if (provider.getPerson() != null && provider.getPerson().getPersonName() != null) {
            PersonName personName = provider.getPerson().getPersonName();
            String prefix = nullToEmpty(personName.getPrefix()).trim();
            String fullName = joinNameParts(
                    personName.getGivenName(),
                    personName.getMiddleName(),
                    personName.getFamilyName());
            if (StringUtils.isBlank(fullName)) {
                return prefix; // fallback
            }
            return StringUtils.isNotBlank(prefix) ? prefix + " " + fullName : fullName;
        }
        // name-only provider (no person linked)
        return nullToEmpty(provider.getName()).trim();
    }

    private static String joinNameParts(String... parts) {
        return Arrays.stream(parts)
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}