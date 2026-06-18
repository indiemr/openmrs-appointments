package org.openmrs.module.appointments.helper;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;

import java.util.List;

public final class AppointmentReminderLocationUtil {

    public static final String LOCATION_ATTR_REMINDER_SMS_ENABLED_UUID = "3565d95d-612f-49b2-9ae6-f75efdf4a46c";
    private static final Log log = LogFactory.getLog(AppointmentReminderLocationUtil.class);


    private AppointmentReminderLocationUtil() {}

    public static boolean isReminderSmsEnabledForLocation(Location location) {
        if (location == null) {
            log.info("Reminder SMS skipped: appointment has no location");
            return false;
        }

        LocationService locationService = Context.getLocationService();
        LocationAttributeType attributeType = locationService
                .getLocationAttributeTypeByUuid(LOCATION_ATTR_REMINDER_SMS_ENABLED_UUID);
        if (attributeType == null) {
            log.warn("Reminder SMS disabled: location attribute type not found for uuid="
                    + LOCATION_ATTR_REMINDER_SMS_ENABLED_UUID);
            return false;
        }

        Location current = locationService.getLocationByUuid(location.getUuid());
        while (current != null) {
            Boolean enabled = getReminderSmsEnabledValue(current, attributeType);
            if (enabled != null) {
                log.info("Reminder SMS for location '" + current.getName() + "' (uuid=" + current.getUuid()
                + "): enabled=" + enabled);
                return enabled;
            }
            Location parent = current.getParentLocation();
            current = parent != null ? locationService.getLocationByUuid(parent.getUuid()) : null;
        }
        log.info("Reminder SMS disabled: no '" + attributeType.getName()
        + "' attribute set on location or parent chain (appointment location uuid="
        + location.getUuid() + ")");
        return false;
    }

    private static Boolean getReminderSmsEnabledValue(Location location, LocationAttributeType attributeType) {
        List<LocationAttribute> attributes = location.getActiveAttributes(attributeType);
        if (CollectionUtils.isEmpty(attributes)) {
            return null;
        }
        String value = attributes.get(0).getValueReference();
        return value != null && Boolean.parseBoolean(value);
    }
}
