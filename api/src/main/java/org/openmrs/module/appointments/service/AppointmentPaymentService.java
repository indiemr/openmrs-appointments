package org.openmrs.module.appointments.service;

import org.openmrs.module.appointments.model.AppointmentPayment;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consumer-facing API to record payments against an existing appointment bill.
 * Callers only need appointment uuid + payments; they do not save the full appointment.
 */
public interface AppointmentPaymentService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void addPayments(String appointmentUuid, List<AppointmentPayment> payments);
}
