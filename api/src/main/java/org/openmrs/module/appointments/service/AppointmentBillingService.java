package org.openmrs.module.appointments.service;

import org.openmrs.module.appointments.model.Appointment;

public interface AppointmentBillingService {
    String createBillForAppointment(Appointment appointment);
}