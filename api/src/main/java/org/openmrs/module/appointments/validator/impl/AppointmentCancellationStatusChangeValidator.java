package org.openmrs.module.appointments.validator.impl;

import java.util.List;

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
            errors.add("This appointment can no longer be cancelled because the cancellation cutoff has passed.");
        }
    }
}
