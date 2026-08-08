package org.openmrs.module.appointments.validator.impl;


import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.util.AppointmentDateOnlyUtil;
import org.openmrs.module.appointments.validator.AppointmentValidator;
import java.text.ParseException;
import java.util.List;

public class DateOnlyAppointmentValidator implements AppointmentValidator {
    
    @Override
    public void validate(Appointment appointment, List<String> errors) {
        if (appointment == null || !appointment.isDateOnlyAppointment()) {
            return;
        }

        if (appointment.getAppointmentDate() == null) {
            errors.add("appointmentDate is required when dateOnly is true");
            return;
        }

        if (appointment.getStartDateTime() != null || appointment.getEndDateTime() != null) {
            errors.add("startDateTime and endDateTime must not be set when dateOnly is true");
        }
    }


    public static void validateRequest(Boolean dateOnly, String appointmentDate, List<String> errors) {
        if (!Boolean.TRUE.equals(dateOnly)) {
            return;
        }
        if (StringUtils.isBlank(appointmentDate)) {
            errors.add("appointmentDate is required when dateOnly is true");
            return;
        }
        try {
            AppointmentDateOnlyUtil.parseAppointmentDate(appointmentDate);
        } catch (ParseException e) {
            errors.add("appointmentDate must be in yyyy-MM-dd format");
        }
    }
}
