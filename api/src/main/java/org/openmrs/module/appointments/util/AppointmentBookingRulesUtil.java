package org.openmrs.module.appointments.util;


import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public final class AppointmentBookingRulesUtil {
    
    private static final String LOCATION_TIMEZONE_ATTRIBUTE = "timezone";

    private AppointmentBookingRulesUtil() {}

    public static boolean isDateWithinBookingWindow(AppointmentServiceDefinition service, Date sloDate) {
        if (service == null || sloDate == null) {
            return true;
        }

        Integer bookAheadDays = service.getBookAheadDays();
        if (bookAheadDays == null) {
            return true;
        }

        ZoneId zoneId = resolveZoneId(service.getLocation());
        LocalDate today = LocalDate.now(zoneId);
        LocalDate slotLocalDate = sloDate.toInstant().atZone(zoneId).toLocalDate();
        LocalDate lastBookableDate = today.plusDays(bookAheadDays);

        return !slotLocalDate.isAfter(lastBookableDate);
    }

    public static boolean isAppointmentWithinBookingWindow(Appointment appointment) {
        if (appointment == null || appointment.getService() == null || appointment.getStartDateTime() == null) {
            return true;
        }

        return isDateWithinBookingWindow(appointment.getService(), appointment.getStartDateTime());
    }

    public static void validateBookAheadDays(Integer bookAheadDays) {
        if (bookAheadDays != null && bookAheadDays < 0) {
            throw new IllegalArgumentException("bookAheadDays must be zero or positive");
        }
    }

    public static ZoneId resolveZoneId(Location location) {
        if (location != null && location.getActiveAttributes() != null) {
            for (LocationAttribute attribute : location.getActiveAttributes()) {
                if (attribute.getAttributeType() != null
                        && LOCATION_TIMEZONE_ATTRIBUTE.equalsIgnoreCase(attribute.getAttributeType().getName())
                        && attribute.getValueReference() != null) {
                    return ZoneId.of(attribute.getValueReference());
                }
            }
        }
        return ZoneId.systemDefault();
    }
}
