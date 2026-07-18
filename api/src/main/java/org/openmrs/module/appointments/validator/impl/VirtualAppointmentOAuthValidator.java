package org.openmrs.module.appointments.validator.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentKind;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.validator.AppointmentValidator;
import org.openmrs.module.indiemroauthprovider.api.OAuthConnectService;
import org.openmrs.module.indiemroauthprovider.dto.AccountStatusResponse;
import org.openmrs.module.indiemroauthprovider.model.OAuthVendorCode;
import java.util.List;
import java.util.Set;

public class VirtualAppointmentOAuthValidator implements AppointmentValidator {
    
    private static final Log log = LogFactory.getLog(VirtualAppointmentOAuthValidator.class);

    private static final String OAUTH_PROVIDER_MODULE_ID = "indiemroauthprovider";
    private static final String OAUTH_PROVIDER_CODE = "GOOGLE";
    private static final String GOOGLE_ACCOUNT_REQUIRED =
            "Google account is required to create tele consultation";

    @Override
    public void validate(Appointment appointment, List<String> errors) {
        if (appointment == null || !AppointmentKind.Virtual.equals(appointment.getAppointmentKind())) {
            return;
        }

        Provider provider = resolveProvider(appointment);
        if (provider == null) {
            errors.add("Provider is required to create a virtual appointment");
            return;
        }

        OAuthConnectService oAuthConnectService = getOAuthConnectService();
        if (oAuthConnectService == null) {
            errors.add(GOOGLE_ACCOUNT_REQUIRED);
            return;
        }

        try {
            AccountStatusResponse status = oAuthConnectService.getAccountStatus(provider, OAuthVendorCode.fromCode(OAUTH_PROVIDER_CODE));
            if (status == null) {
                errors.add(GOOGLE_ACCOUNT_REQUIRED);
            }
        } catch (Exception e) {
            log.error("Failed to check Google OAuth status for provider " + provider.getUuid(), e);
            errors.add(GOOGLE_ACCOUNT_REQUIRED);
        }
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
        return null;
    }

    private OAuthConnectService getOAuthConnectService() {
        Module module = ModuleFactory.getModuleById(OAUTH_PROVIDER_MODULE_ID);
        if (module == null || !module.isStarted()) {
            return null;
        }
        try {
            Class<?> serviceClass = ModuleFactory.getModuleClassLoader(module)
                    .loadClass("org.openmrs.module.indiemroauthprovider.api.OAuthConnectService");
            return (OAuthConnectService) Context.getService(serviceClass);
        } catch (ClassCastException e) {
            log.warn("OAuthConnectService classloader mismatch; ensure indiemroauthprovider-api is not bundled in appointments lib/", e);
            return null;
        } catch (Exception e) {
            log.warn("Unable to resolve OAuthConnectService from " + OAUTH_PROVIDER_MODULE_ID, e);
            return null;
        }
    }
}
