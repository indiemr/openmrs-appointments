package org.openmrs.module.appointments.helper;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.indiemrcore.resourcesource.api.IndiemrResourceSourceService;
import org.openmrs.module.indiemrcore.resourcesource.model.IndiemrResourceSource;
import org.openmrs.module.indiemrcore.resourcesource.model.ResourceSource;
import org.openmrs.module.indiemrcore.resourcesource.model.ResourceType;


public final class ResourceSourceHelper {

    private ResourceSourceHelper() {
    }

    public static void recordHold(AppointmentHold hold) {
        if (hold.getSource() == null || hold.getSource() == ResourceSource.CLINIC) {
            return;
        }
        service().record(ResourceType.APPOINTMENT_HOLD, hold.getUuid(), hold.getSource(),
                hold.getSourcePartner(), hold.getExternalRef());
    }

    /** Copies the converting hold's source onto a newly created appointment. */
    public static void recordAppointmentFromHold(Appointment appointment) {
        String holdUuid = appointment.getConvertingHoldUuid();
        if (StringUtils.isBlank(holdUuid)) {
            return;
        }
        IndiemrResourceSource holdSource = service().get(ResourceType.APPOINTMENT_HOLD, holdUuid);
        if (holdSource == null) {
            return;
        }
        service().record(ResourceType.APPOINTMENT, appointment.getUuid(), holdSource.getSource(),
                holdSource.getSourcePartner(), holdSource.getExternalRef());
    }

    private static IndiemrResourceSourceService service() {
        return Context.getService(IndiemrResourceSourceService.class);
    }
}
