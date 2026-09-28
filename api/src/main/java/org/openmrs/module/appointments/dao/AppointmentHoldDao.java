package org.openmrs.module.appointments.dao;

import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;

import java.util.Date;

public interface AppointmentHoldDao {

    AppointmentHold save(AppointmentHold hold);

    AppointmentHold getByUuid(String uuid);

    int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                                  Date slotStart,
                                  Date slotEnd, 
                                  String excludeHoldUuid);

    boolean consumeIfActive(String holdUuid, Appointment appointment);

    boolean expireIfDue(String holdUuid);

    int expireAllDue();

    boolean acquireSlotLock(String lockKey, int timeoutSeconds);

    void releaseSlotLock(String lockKey);
}