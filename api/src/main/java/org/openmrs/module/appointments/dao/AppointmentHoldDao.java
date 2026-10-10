package org.openmrs.module.appointments.dao;

import org.openmrs.Patient;
import org.openmrs.User;
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

    int countActiveForPatient(Patient patient);

    int countUnusedForPatientSince(Patient patient, Date since);

    boolean consumeIfActive(String holdUuid, Appointment appointment);

    boolean expireIfDue(String holdUuid);

    boolean releaseIfHeld(String holdUuid, User releasedBy, Date releasedAt);

    boolean extendIfActive(AppointmentHold hold, Date newExpiresAt, User extendedBy, Date extendedAt);

    int expireAllDue();

    boolean acquireSlotLock(String lockKey, int timeoutSeconds);

    void releaseSlotLock(String lockKey);
}