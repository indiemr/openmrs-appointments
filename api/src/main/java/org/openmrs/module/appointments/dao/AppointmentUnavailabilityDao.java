package org.openmrs.module.appointments.dao;

import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentUnavailability;
import org.openmrs.module.appointments.search.param.AppointmentUnavailabilitySearchParams;

import java.util.Date;
import java.util.List;

public interface AppointmentUnavailabilityDao {

    AppointmentUnavailability save(AppointmentUnavailability appointmentUnavailability);

    AppointmentUnavailability getByUuid(String uuid);

    List<AppointmentUnavailability> getAll(AppointmentUnavailabilitySearchParams searchParams);

    List<AppointmentUnavailability> findOverlappingForServiceOnDate(Location location,
                                                                    AppointmentServiceDefinition service,
                                                                    Provider provider,
                                                                    Date dayStart,
                                                                    Date dayEnd);
}
