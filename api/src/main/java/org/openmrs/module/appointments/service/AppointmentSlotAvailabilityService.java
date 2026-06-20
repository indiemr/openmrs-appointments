package org.openmrs.module.appointments.service;

import static org.openmrs.module.appointments.constants.PrivilegeConstants.MANAGE_APPOINTMENTS_SERVICE;
import static org.openmrs.module.appointments.constants.PrivilegeConstants.VIEW_APPOINTMENTS_SERVICE;

import java.util.Date;
import java.util.List;

import org.openmrs.annotation.Authorized;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentSlotAvailability;
import org.springframework.transaction.annotation.Transactional;

public interface AppointmentSlotAvailabilityService {
    
    @Transactional(readOnly = true)
    @Authorized({VIEW_APPOINTMENTS_SERVICE, MANAGE_APPOINTMENTS_SERVICE})
    List<AppointmentSlotAvailability> getAvailableSlots(String serviceUuid, Date date, String excludeAppointmentUuid);

    @Transactional(readOnly = true)
    boolean isSlotCapacityExceeded(Appointment appointment);
}
