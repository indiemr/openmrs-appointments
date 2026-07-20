package org.openmrs.module.appointments.service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface AppointmentCalendarService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String createCalendarEventForAppointment(String appointmentUuid);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateCalendarEventForAppointment(String appointmentUuid);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void cancelCalendarEventForAppointment(String appointmentUuid);
}