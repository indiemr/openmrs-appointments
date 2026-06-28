package org.openmrs.module.appointments.conflicts.impl;

import org.openmrs.module.appointments.conflicts.AppointmentConflict;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentConflictType;
import org.openmrs.module.appointments.service.AppointmentSlotAvailabilityService;

import java.util.ArrayList;
import java.util.List;

import static org.openmrs.module.appointments.model.AppointmentConflictType.APPOINTMENT_UNAVAILABLE;

public class AppointmentUnavailabilityConflict implements AppointmentConflict {

    private AppointmentSlotAvailabilityService appointmentSlotAvailabilityService;

    public void setAppointmentSlotAvailabilityService(AppointmentSlotAvailabilityService appointmentSlotAvailabilityService) {
        this.appointmentSlotAvailabilityService = appointmentSlotAvailabilityService;
    }

    @Override
    public AppointmentConflictType getType() {
        return APPOINTMENT_UNAVAILABLE;
    }

    @Override
    public List<Appointment> getConflicts(List<Appointment> appointments) {
        List<Appointment> conflictingAppointments = new ArrayList<>();
        if (appointments == null) {
            return conflictingAppointments;
        }
        for (Appointment appointment : appointments) {
            if (appointmentSlotAvailabilityService.isSlotBlocked(appointment)) {
                conflictingAppointments.add(appointment);
            }
        }
        return conflictingAppointments;
    }
}
