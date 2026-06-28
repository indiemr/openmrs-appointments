package org.openmrs.module.appointments.util;

import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentUnavailability;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public final class AppointmentUnavailabilityUtil {

    private AppointmentUnavailabilityUtil() {}

    public static boolean isSlotBlocked(List<AppointmentUnavailability> blocks,
                                        AppointmentServiceDefinition service,
                                        Date slotStart,
                                        Date slotEnd) {
        if (blocks == null || blocks.isEmpty() || service == null || slotStart == null || slotEnd == null) {
            return false;
        }

        Location location = service.getLocation();
        Provider provider = service.getProvider();

        for (AppointmentUnavailability block : blocks) {
            if (!matchesScope(block, service, location, provider)) {
                continue;
            }
            Date blockStart = combineDateAndTime(block.getStartDate(), block.getStartTime());
            Date blockEnd = combineDateAndTime(block.getEndDate(), block.getEndTime());
            if (slotStart.before(blockEnd) && slotEnd.after(blockStart)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesScope(AppointmentUnavailability block,
                                       AppointmentServiceDefinition service,
                                       Location location,
                                       Provider provider) {
        if (block == null || location == null || block.getLocation() == null) {
            return false;
        }
        if (!block.getLocation().getLocationId().equals(location.getLocationId())) {
            return false;
        }
        if (block.getService() != null) {
            if (service == null || block.getService().getAppointmentServiceId() == null
                    || !block.getService().getAppointmentServiceId().equals(service.getAppointmentServiceId())) {
                return false;
            }
        }
        if (block.getProvider() != null) {
            if (provider == null || block.getProvider().getProviderId() == null
                    || !block.getProvider().getProviderId().equals(provider.getProviderId())) {
                return false;
            }
        }
        return true;
    }

    public static Date combineDateAndTime(java.sql.Date date, Time time) {
        if (date == null || time == null) {
            return null;
        }
        LocalDate localDate = date.toLocalDate();
        LocalTime localTime = time.toLocalTime();
        return Date.from(LocalDateTime.of(localDate, localTime).atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date startOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date endOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    public static java.sql.Date toSqlDate(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return java.sql.Date.valueOf(localDate);
    }
}
