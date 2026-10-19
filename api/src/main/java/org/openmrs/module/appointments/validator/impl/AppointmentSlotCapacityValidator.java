package org.openmrs.module.appointments.validator.impl;


import org.openmrs.module.appointments.exception.AppointmentErrorReason;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.service.AppointmentSlotAvailabilityService;
import org.openmrs.module.appointments.validator.AppointmentValidator;
import java.util.List;

public class AppointmentSlotCapacityValidator implements AppointmentValidator {
    private AppointmentSlotAvailabilityService appointmentSlotAvailabilityService;

    public void setAppointmentSlotAvailabilityService(AppointmentSlotAvailabilityService appointmentSlotAvailabilityService) {
        this.appointmentSlotAvailabilityService = appointmentSlotAvailabilityService;
    }

    @Override
    public void validate(Appointment appointment, List<String> errors) {
        if (appointment == null || appointment.getService() == null || appointment.getStartDateTime() == null) {
            return;
        }
        if (AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
            return;
        }
        if (appointmentSlotAvailabilityService.isSlotBlocked(appointment)) {
            errors.add(AppointmentErrorReason.SLOT_UNAVAILABLE.getMessage());
            return;
        }
        if (appointmentSlotAvailabilityService.isSlotCapacityExceeded(appointment)) {
            errors.add(AppointmentErrorReason.SLOT_FULL.getMessage());
        }
    }
}
