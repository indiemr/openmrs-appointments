package org.openmrs.module.appointments.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class AppointmentDateOnlyUtil {
    public static final String APPOINTMENT_DATE_PATTERN = "yyyy-MM-dd";

    private AppointmentDateOnlyUtil() {}

    public static Date parseAppointmentDate(String appointmentDate) throws ParseException {
        if (StringUtils.isBlank(appointmentDate)) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(APPOINTMENT_DATE_PATTERN);
        format.setLenient(false);
        return DateUtils.truncate(format.parse(appointmentDate.trim()), Calendar.DAY_OF_MONTH);
    }
    
    public static String formatAppointmentDate(Date appointmentDate) {
        if (appointmentDate == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(APPOINTMENT_DATE_PATTERN);
        return format.format(appointmentDate);
    }
}
