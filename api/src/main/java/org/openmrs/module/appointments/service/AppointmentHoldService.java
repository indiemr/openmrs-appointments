package org.openmrs.module.appointments.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.springframework.transaction.annotation.Transactional;

import static org.openmrs.module.appointments.constants.PrivilegeConstants.MANAGE_APPOINTMENTS;
import static org.openmrs.module.appointments.constants.PrivilegeConstants.VIEW_APPOINTMENTS;

public interface AppointmentHoldService {

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    AppointmentHold createHold(AppointmentHold hold);

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    AppointmentHold consumeHold(String holdUuid, Appointment appointment);

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    boolean expireHold(String holdUuid);

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    int expireDueHolds();

    @Transactional(readOnly = true)
    @Authorized({VIEW_APPOINTMENTS})
    AppointmentHold getByUuid(String uuid);
}