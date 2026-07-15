package org.openmrs.module.appointments.dao;


import org.openmrs.Provider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceSearchParams;
import org.openmrs.module.appointments.model.AppointmentServiceType;

import java.util.List;

public interface AppointmentServiceDao {

    List<AppointmentServiceDefinition> getAllAppointmentServices(boolean includeVoided, List<String> locationIds);

    AppointmentServiceDefinition save(AppointmentServiceDefinition appointmentServiceDefinition);

    AppointmentServiceDefinition getAppointmentServiceByUuid(String uuid);

    AppointmentServiceDefinition getNonVoidedAppointmentServiceByName(String serviceName, Provider provider);

    AppointmentServiceType getAppointmentServiceTypeByUuid(String uuid);

    List<AppointmentServiceDefinition> search(AppointmentServiceSearchParams searchParams);
}
