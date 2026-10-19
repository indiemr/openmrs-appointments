package org.openmrs.module.appointments.validator.impl;

import java.util.List;

import org.openmrs.module.appointments.exception.AppointmentErrorReason;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.util.AppointmentBookingRulesUtil;
import org.openmrs.module.appointments.validator.AppointmentValidator;

public class AppointmentBookingWindowValidator implements AppointmentValidator {
    
    @Override
    public void validate(Appointment appointment, List<String> errors) {
        if (appointment == null
                || appointment.getService() == null
                || AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
            return;
        }

        if (appointment.isDateOnlyAppointment()) {
            if (appointment.getAppointmentDate() == null) {
                return;
            }
            if (!AppointmentBookingRulesUtil.isDateWithinBookingWindow(
                    appointment.getService(), appointment.getAppointmentDate())) {
                errors.add(AppointmentErrorReason.OUTSIDE_BOOKING_WINDOW.getMessage());
            }
            return;
        }

        if (appointment.getStartDateTime() == null) {
            return;
        }
        if (!AppointmentBookingRulesUtil.isAppointmentWithinBookingWindow(appointment)) {
            errors.add(AppointmentErrorReason.OUTSIDE_BOOKING_WINDOW.getMessage());
        }
    }
}
