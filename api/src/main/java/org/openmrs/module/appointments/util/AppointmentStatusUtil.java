package org.openmrs.module.appointments.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.openmrs.module.appointments.model.AppointmentStatus;

public class AppointmentStatusUtil {
    private AppointmentStatusUtil() {}

    public static boolean isTentative(AppointmentStatus status) {
        return status == AppointmentStatus.WaitList || status == AppointmentStatus.Tentative;
    }

    public static boolean isConfirmed(AppointmentStatus status) {
        return status == AppointmentStatus.Confirmed || status == AppointmentStatus.Scheduled;
    }

    public static List<AppointmentStatus> confirmedStatuses() {
        return Collections.unmodifiableList(Arrays.asList(
            AppointmentStatus.Scheduled, AppointmentStatus.Confirmed
        ));
    }

    public static List<AppointmentStatus> tentativeStatuses() {
        return Collections.unmodifiableList(Arrays.asList(
            AppointmentStatus.Tentative, AppointmentStatus.WaitList
        ));
    }

    /** Reset target: Scheduled or Confirmed */
    public static boolean isResetToConfirmed(AppointmentStatus toStatus) {
        return isConfirmed(toStatus);
    }
    
}
