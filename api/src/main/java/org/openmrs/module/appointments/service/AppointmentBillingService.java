package org.openmrs.module.appointments.service;

import org.openmrs.module.appointments.model.AppointmentPayment;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AppointmentBillingService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String createBillForAppointment(String appointmentUuid, boolean createBill);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void addPaymentsForAppointment(String appointmentUuid, List<AppointmentPayment> payments);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void voidBillForAppointment(String appointmentUuid, String voidReason);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String syncBillWithAppointmentService(String appointmentUuid);
}