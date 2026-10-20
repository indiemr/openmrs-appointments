package org.openmrs.module.appointments.validator.impl;

import java.util.List;

import org.openmrs.module.appointments.exception.AppointmentErrorReason;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.util.AppointmentBookingRulesUtil;
import org.openmrs.module.appointments.validator.AppointmentStatusChangeValidator;

public class AppointmentCancellationStatusChangeValidator implements AppointmentStatusChangeValidator {
    @Override
    public void validate(Appointment appointment, AppointmentStatus toStatus, List<String> errors) {
        if (!AppointmentStatus.Cancelled.equals(toStatus)) {
            return;
        }
        if (!AppointmentBookingRulesUtil.isCancellationAllowed(appointment)) {
            errors.add(AppointmentErrorReason.CANCELLATION_CUTOFF_PASSED.getMessage());
        }
    }
}
