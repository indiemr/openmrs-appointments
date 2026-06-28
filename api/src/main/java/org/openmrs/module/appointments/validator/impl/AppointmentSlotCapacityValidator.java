package org.openmrs.module.appointments.validator.impl;


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
            errors.add("Selected time slot is unavailable. Please choose another slot.");
            return;
        }
        if (appointmentSlotAvailabilityService.isSlotCapacityExceeded(appointment)) {
            errors.add("Selected time slot is full. Please choose another slot.");
        }
    }
}
