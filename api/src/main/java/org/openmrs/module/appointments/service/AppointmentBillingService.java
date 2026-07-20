package org.openmrs.module.appointments.service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface AppointmentBillingService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String createBillForAppointment(String appointmentUuid, boolean createBill);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void voidBillForAppointment(String appointmentUuid, String voidReason);
}