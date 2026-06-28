package org.openmrs.module.appointments.util;

import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.model.ServiceWeeklyAvailability;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.openmrs.api.context.Context;

public final class AppointmentServiceCapacityUtil {
    public static final String GP_DEFAULT_MINUTES_PER_APPOINTMENT = "appointments.defaultMinutesPerAppointment";
    public static final String GP_DEFAULT_DURATION_MINUTES = "appointments.defaultSlotDurationMinutes";

    // fallback if GP missing/invalid
    public static final int DEFAULT_MINUTES_PER_APPOINTMENT = 5;
    public static final int DEFAULT_DURATION_MINUTES = 15;
    public static final List<AppointmentStatus> OCCUPYING_STATUSES = Collections.unmodifiableList(Arrays.asList(
        AppointmentStatus.Scheduled,
        AppointmentStatus.CheckedIn,
        AppointmentStatus.Requested,
        AppointmentStatus.Arrived
    ));

    private AppointmentServiceCapacityUtil() {}

    public static int resolveSlotCapacity(
            AppointmentServiceDefinition service,
            ServiceWeeklyAvailability weeklyAvailability,
            int durationMins) {

        Integer configured = resolveMaxAppointmentsPerSlot(service, weeklyAvailability);
        if (configured != null) {
            return configured;
        }
        return Math.max(1, durationMins / getDefaultMinutesPerAppointment());
    }

    private static int getGlobalPropertyInt(String propertyName, int defaultValue) {
        try {
            String value = Context.getAdministrationService()
                    .getGlobalProperty(propertyName, String.valueOf(defaultValue));
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getDefaultMinutesPerAppointment() {
        return getGlobalPropertyInt(GP_DEFAULT_MINUTES_PER_APPOINTMENT, DEFAULT_MINUTES_PER_APPOINTMENT);
    }

    public static int getDefaultDurationMinutes() {
        return getGlobalPropertyInt(GP_DEFAULT_DURATION_MINUTES, DEFAULT_DURATION_MINUTES);
    }

    public static Integer resolveMaxAppointmentsPerSlot(AppointmentServiceDefinition service, ServiceWeeklyAvailability weeklyAvailability) {
        if (weeklyAvailability != null && weeklyAvailability.getMaxAppointmentsPerSlot() != null) {
            return weeklyAvailability.getMaxAppointmentsPerSlot();
        }
        return service != null ? service.getMaxAppointmentsPerSlot() : null;
    }

    public static List<ServiceWeeklyAvailability> getWeeklyAvailabilitiesForDay(AppointmentServiceDefinition service, DayOfWeek dayOfWeek) {
        if (service == null || service.getWeeklyAvailability() == null) {
            return Collections.emptyList();
        }
        return service.getWeeklyAvailability().stream()
                .filter(avail -> !avail.getVoided())
                .filter(avail -> avail.getDayOfWeek() == dayOfWeek)
                .collect(Collectors.toList());
    }

    public static boolean hasWeeklyAvailability(AppointmentServiceDefinition service) {
        return service != null && service.getWeeklyAvailability() != null && service.getWeeklyAvailability().stream().anyMatch(avail -> !avail.getVoided());
    }

    public static int resolveDurationMins(AppointmentServiceDefinition service, AppointmentServiceType serviceType) {
        if (service != null && service.getDurationMins() != null) {
            return service.getDurationMins();
        }

        if (serviceType != null && serviceType.getDuration() != null) {
            return serviceType.getDuration();
        }
        return getDefaultDurationMinutes();
    }

    public static int resolveDurationMins(Appointment appointment) {
        return resolveDurationMins(appointment.getService(), appointment.getServiceType());
    }

    public static Date resolveAppointmentEndDateTime(Appointment appointment) {
        if (appointment.getEndDateTime() != null) {
            return appointment.getEndDateTime();
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(appointment.getStartDateTime());
        calendar.add(Calendar.MINUTE, resolveDurationMins(appointment));
        return calendar.getTime();
    }

    public static List<Date[]> generateSlots(Date date, Time startTime, Time endTime, int durationMins) {
        if (date == null || startTime == null || endTime == null || durationMins <= 0) {
            return Collections.emptyList();
        }

        List<Date[]> slots = new ArrayList<>();
        Calendar slotStart = combineDateAndTime(date, startTime);
        Calendar windowEnd = combineDateAndTime(date, endTime);

        while (true) {
            Calendar slotEnd = (Calendar) slotStart.clone();
            slotEnd.add(Calendar.MINUTE, durationMins);
            if (slotEnd.after(windowEnd)) {
                break;
            }

            slots.add(new Date[]{slotStart.getTime(), slotEnd.getTime()});
            slotStart = slotEnd;
        }
        return slots;
    }

    public static DayOfWeek toDayOfWeek(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.getDayOfWeek();
    }
    private static Calendar combineDateAndTime(Date date, Time time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        Calendar timeCalendar = Calendar.getInstance();
        timeCalendar.setTime(time);
        calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY));
        calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE));
        calendar.set(Calendar.SECOND, timeCalendar.get(Calendar.SECOND));
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }
}
