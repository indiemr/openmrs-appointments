package org.openmrs.module.appointments.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.appointments.dao.AppointmentDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentKind;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.service.AppointmentCalendarService;
import org.openmrs.module.appointments.util.AppointmentServiceCapacityUtil;
import org.openmrs.module.indiemroauthprovider.api.TeleconsultService;
import org.openmrs.module.indiemroauthprovider.dto.CancelCalendarEventRequest;
import org.openmrs.module.indiemroauthprovider.dto.CreateCalendarEventRequest;
import org.openmrs.module.indiemroauthprovider.dto.CreateCalendarEventResponse;
import org.openmrs.module.indiemroauthprovider.dto.UpdateCalendarEventRequest;

import java.util.Collection;
import java.util.Set;

public class AppointmentCalendarServiceImpl implements AppointmentCalendarService {

    private static final Log log = LogFactory.getLog(AppointmentCalendarServiceImpl.class);
    private static final String OAUTH_PROVIDER_MODULE_ID = "indiemroauthprovider";
    private static final String OAUTH_PROVIDER_CODE = "GOOGLE";
    private static final String RESOURCE_TYPE = "APPOINTMENT";
    private static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";

    private AppointmentDao appointmentDao;

    public void setAppointmentDao(AppointmentDao appointmentDao) {
        this.appointmentDao = appointmentDao;
    }

    @Override
    public void createCalendarEventForAppointment(Appointment appointment) {
        if (!shouldSyncToCalendar(appointment)) {
            return;
        }

        Provider provider = resolveProvider(appointment);
        if (provider == null) {
            log.warn("Skipping calendar event for appointment " + appointment.getUuid() + ": no provider resolved");
            return;
        }

        TeleconsultService teleconsultService = getTeleconsultService();
        if (teleconsultService == null) {
            log.warn("Skipping calendar event for appointment " + appointment.getUuid() + ": TeleconsultService not available");
            return;
        }

        try {
            CreateCalendarEventRequest request = buildCreateRequest(appointment);
            CreateCalendarEventResponse response = teleconsultService.createCalendarEvent(provider, request);

            if (isVirtual(appointment) && response != null && StringUtils.isNotBlank(response.getMeetingUrl())) {
                appointment.setTeleHealthVideoLink(response.getMeetingUrl());
                appointmentDao.save(appointment);
            }

            log.info("Created calendar event for appointment " + appointment.getUuid()
                    + (response != null && response.getHtmlLink() != null ? " at " + response.getHtmlLink() : ""));
        } catch (Exception e) {
            log.error("Failed to create calendar event for appointment " + appointment.getUuid(), e);
        }
    }

    @Override
    public void updateCalendarEventForAppointment(Appointment appointment) {
        if (!shouldSyncToCalendar(appointment)) {
            return;
        }

        Provider provider = resolveProvider(appointment);
        if (provider == null) {
            log.warn("Skipping calendar update for appointment " + appointment.getUuid() + ": no provider resolved");
            return;
        }

        TeleconsultService teleconsultService = getTeleconsultService();
        if (teleconsultService == null) {
            log.warn("Skipping calendar update for appointment " + appointment.getUuid() + ": TeleconsultService not available");
            return;
        }

        try {
            UpdateCalendarEventRequest request = buildUpdateRequest(appointment);
            teleconsultService.updateCalendarEvent(provider, request);
            log.info("Updated calendar event for appointment " + appointment.getUuid());
        } catch (Exception e) {
            log.error("Failed to update calendar event for appointment " + appointment.getUuid(), e);
        }
    }

    @Override
    public void cancelCalendarEventForAppointment(Appointment appointment) {
        if (!shouldSyncToCalendar(appointment)) {
            return;
        }

        Provider provider = resolveProvider(appointment);
        if (provider == null) {
            log.warn("Skipping calendar cancel for appointment " + appointment.getUuid() + ": no provider resolved");
            return;
        }

        TeleconsultService teleconsultService = getTeleconsultService();
        if (teleconsultService == null) {
            log.warn("Skipping calendar cancel for appointment " + appointment.getUuid() + ": TeleconsultService not available");
            return;
        }

        try {
            CancelCalendarEventRequest request = buildCancelCalendarEventRequest(appointment);
            teleconsultService.cancelCalendarEvent(provider, request);
            log.info("Cancelled calendar event for appointment " + appointment.getUuid());
        } catch (Exception e) {
            log.error("Failed to cancel calendar event for appointment " + appointment.getUuid(), e);
        }
    }

    private boolean shouldSyncToCalendar(Appointment appointment) {
        if (!isOAuthProviderModuleStarted() || getTeleconsultService() == null) {
            return false;
        }
        if (appointment == null || appointment.getStartDateTime() == null) {
            return false;
        }
        AppointmentKind kind = appointment.getAppointmentKind();
        return AppointmentKind.Virtual.equals(kind) || AppointmentKind.Scheduled.equals(kind);
    }

    private CancelCalendarEventRequest buildCancelCalendarEventRequest(Appointment appointment) {
        CancelCalendarEventRequest request = new CancelCalendarEventRequest();
        request.setOauthProviderCode(OAUTH_PROVIDER_CODE);
        request.setResourceType(RESOURCE_TYPE);
        request.setResourceUuid(appointment.getUuid());
        return request;
    }

    private CreateCalendarEventRequest buildCreateRequest(Appointment appointment) {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest();
        request.setOauthProviderCode(OAUTH_PROVIDER_CODE);
        request.setTitle(buildEventTitle(appointment));
        request.setResourceType(RESOURCE_TYPE);
        request.setResourceUuid(appointment.getUuid());
        request.setStart(appointment.getStartDateTime());
        request.setEnd(AppointmentServiceCapacityUtil.resolveAppointmentEndDateTime(appointment));
        request.setTimeZone(resolveTimeZone());
        request.setCreateMeet(isVirtual(appointment));
        request.setMintJoinLink(isVirtual(appointment));
        return request;
    }

    private UpdateCalendarEventRequest buildUpdateRequest(Appointment appointment) {
        UpdateCalendarEventRequest request = new UpdateCalendarEventRequest();
        request.setOauthProviderCode(OAUTH_PROVIDER_CODE);
        request.setResourceType(RESOURCE_TYPE);
        request.setResourceUuid(appointment.getUuid());
        request.setTitle(buildEventTitle(appointment));
        request.setStart(appointment.getStartDateTime());
        request.setEnd(AppointmentServiceCapacityUtil.resolveAppointmentEndDateTime(appointment));
        request.setTimeZone(resolveTimeZone());
        return request;
    }

    private String buildEventTitle(Appointment appointment) {
        String prefix = isVirtual(appointment) ? "Teleconsultation" : "Appointment";
        return prefix + " - " + buildPatientDetails(appointment);
    }
    
    private String buildPatientDetails(Appointment appointment) {
        StringBuilder details = new StringBuilder(resolvePatientName(appointment));
    
        String patientId = resolvePatientIdentifier(appointment);
        if (StringUtils.isNotBlank(patientId)) {
            details.append(" (").append(patientId).append(")");
        }
    
        String phone = resolvePatientPhoneNumber(appointment);
        if (StringUtils.isNotBlank(phone)) {
            details.append(" - ").append(phone);
        }
    
        String locationName = resolveLocationName(appointment);
        if (StringUtils.isNotBlank(locationName)) {
            details.append(" - at ").append(locationName);
        }
    
        return details.toString();
    }
    
    private String resolvePatientIdentifier(Appointment appointment) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getPatientIdentifier() == null) {
            return null;
        }
        return patient.getPatientIdentifier().getIdentifier();
    }
    
    private String resolvePatientPhoneNumber(Appointment appointment) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getAttribute("phoneNumber") == null) {
            return null;
        }
        return patient.getAttribute("phoneNumber").getValue();
    }
    
    private String resolveLocationName(Appointment appointment) {
        Location location = appointment.getLocation();
        if (location == null) {
            return null;
        }
        String name = location.getName();
        if (StringUtils.isBlank(name)) {
            return name;
        }
    
        Location parent = location.getParentLocation();
        if (parent != null && StringUtils.isNotBlank(parent.getName())) {
            String parentPrefix = parent.getName() + "_";
            if (StringUtils.startsWithIgnoreCase(name, parentPrefix)) {
                name = name.substring(parentPrefix.length());
            }
        }
        return name;
    }

    private String resolvePatientName(Appointment appointment) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getPersonName() == null) {
            return "Patient";
        }
        PersonName personName = patient.getPersonName();
        String fullName = personName.getFullName();
        return StringUtils.isNotBlank(fullName) ? fullName : "Patient";
    }

    private boolean isVirtual(Appointment appointment) {
        return AppointmentKind.Virtual.equals(appointment.getAppointmentKind());
    }

    private boolean isOAuthProviderModuleStarted() {
        Module module = ModuleFactory.getModuleById(OAUTH_PROVIDER_MODULE_ID);
        return module != null && module.isStarted();
    }

    private TeleconsultService getTeleconsultService() {
        Module module = ModuleFactory.getModuleById(OAUTH_PROVIDER_MODULE_ID);
        if (module == null || !module.isStarted()) {
            return null;
        }
        try {
            Class<?> serviceClass = ModuleFactory.getModuleClassLoader(module)
                    .loadClass("org.openmrs.module.indiemroauthprovider.api.TeleconsultService");
            return (TeleconsultService) Context.getService(serviceClass);
        } catch (ClassCastException e) {
            log.warn("TeleconsultService classloader mismatch; ensure indiemroauthprovider-api is not bundled in appointments lib/", e);
            return null;
        } catch (Exception e) {
            if (e.getClass().getSimpleName().contains("ServiceNotFound")) {
                log.warn("TeleconsultService is not registered in OpenMRS context", e);
                return null;
            }
            log.warn("Unable to resolve TeleconsultService from " + OAUTH_PROVIDER_MODULE_ID, e);
            return null;
        }
    }

    private String resolveTimeZone() {
        String configured = Context.getAdministrationService().getGlobalProperty("sms.timezone", DEFAULT_TIME_ZONE);
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_TIME_ZONE;
        }
        if ("IST".equalsIgnoreCase(configured.trim())) {
            return DEFAULT_TIME_ZONE;
        }
        return configured.trim();
    }

    private Provider resolveProvider(Appointment appointment) {
        AppointmentServiceDefinition appointmentService = appointment.getService();
        if (appointmentService != null && appointmentService.getProvider() != null) {
            return appointmentService.getProvider();
        }

        Set<AppointmentProvider> appointmentProviders = appointment.getProviders();
        if (appointmentProviders != null) {
            for (AppointmentProvider appointmentProvider : appointmentProviders) {
                if (appointmentProvider != null
                        && !Boolean.TRUE.equals(appointmentProvider.getVoided())
                        && appointmentProvider.getProvider() != null) {
                    return appointmentProvider.getProvider();
                }
            }
        }

        if (appointment.getProvider() != null) {
            return appointment.getProvider();
        }

        User user = Context.getAuthenticatedUser();
        if (user != null && user.getPerson() != null) {
            ProviderService providerService = Context.getProviderService();
            Collection<Provider> providers = providerService.getProvidersByPerson(user.getPerson(), false);
            if (providers != null && !providers.isEmpty()) {
                return providers.iterator().next();
            }
        }
        return null;
    }
}
