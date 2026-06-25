package org.openmrs.module.appointments.util;

import org.openmrs.Location;
import org.openmrs.api.LocationService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AppointmentServiceLocationUtil {

    private AppointmentServiceLocationUtil() {}

    public static List<String> getSelfAndDescendantLocationUuids(Location parent, LocationService locationService) {
        if (parent == null || locationService == null) {
            return Collections.emptyList();
        }
        Set<String> uuids = new LinkedHashSet<>();
        collectLocationUuids(parent, locationService, uuids);
        return new ArrayList<>(uuids);
    }

    private static void collectLocationUuids(Location location, LocationService locationService, Set<String> uuids) {
        if (location == null) {
            return;
        }
        Location parent = locationService.getLocation(location.getLocationId());
        if (parent == null || Boolean.TRUE.equals(parent.getRetired())) {
            return;
        }
        if (!uuids.add(parent.getUuid())) {
            return;
        }

        // Option A (recommended for 2.4.2): query children via LocationService
        List<Location> children = locationService.getLocations(null, parent, null, false, null, null);
        for (Location child : children) {
            collectLocationUuids(child, locationService, uuids);
        }
    }
}