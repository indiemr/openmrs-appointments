package org.openmrs.module.appointments.validator.impl;

import java.util.List;

import org.openmrs.module.appointments.exception.AppointmentErrorReason;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.util.AppointmentBookingRulesUtil;
import org.openmrs.module.appointments.validator.AppointmentValidator;

public class AppointmentCancellationValidator implements AppointmentValidator {
    @Override
    public void validate(Appointment appointment, List<String> errors) {
        if (appointment == null
                || appointment.getService() == null
                || appointment.getStartDateTime() == null
                || !AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
            return;
        }

        if (!AppointmentBookingRulesUtil.isCancellationAllowed(appointment)) {
            errors.add(AppointmentErrorReason.CANCELLATION_CUTOFF_PASSED.getMessage());
        }
    }
}
