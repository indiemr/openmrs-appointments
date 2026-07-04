package org.openmrs.module.appointments.service;

import org.openmrs.module.appointments.model.Appointment;

public interface AppointmentCalendarService {

    void createCalendarEventForAppointment(Appointment appointment);

    void updateCalendarEventForAppointment(Appointment appointment);

    void cancelCalendarEventForAppointment(Appointment appointment);
}
