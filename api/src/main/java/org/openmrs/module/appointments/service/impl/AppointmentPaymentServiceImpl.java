package org.openmrs.module.appointments.service.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.module.appointments.model.AppointmentPayment;
import org.openmrs.module.appointments.service.AppointmentBillingService;
import org.openmrs.module.appointments.service.AppointmentPaymentService;

import java.util.List;

public class AppointmentPaymentServiceImpl implements AppointmentPaymentService {

    private AppointmentBillingService appointmentBillingService;

    public void setAppointmentBillingService(AppointmentBillingService appointmentBillingService) {
        this.appointmentBillingService = appointmentBillingService;
    }

    @Override
    public void addPayments(String appointmentUuid, List<AppointmentPayment> payments) {
        if (StringUtils.isBlank(appointmentUuid)) {
            throw new IllegalArgumentException("appointmentUuid is required");
        }
        appointmentBillingService.addPaymentsForAppointment(appointmentUuid, payments);
    }
}
