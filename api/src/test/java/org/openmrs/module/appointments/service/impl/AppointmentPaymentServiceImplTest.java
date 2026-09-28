package org.openmrs.module.appointments.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openmrs.module.appointments.model.AppointmentPayment;
import org.openmrs.module.appointments.service.AppointmentBillingService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class AppointmentPaymentServiceImplTest {

    @Mock
    private AppointmentBillingService appointmentBillingService;

    private AppointmentPaymentServiceImpl appointmentPaymentService;

    @Before
    public void setUp() {
        appointmentPaymentService = new AppointmentPaymentServiceImpl();
        appointmentPaymentService.setAppointmentBillingService(appointmentBillingService);
    }

    @Test
    public void shouldDelegateAddPayments() {
        AppointmentPayment payment = new AppointmentPayment();
        payment.setAmountPaying(new BigDecimal("200"));
        payment.setPaymentMode("cash-uuid");
        List<AppointmentPayment> payments = Collections.singletonList(payment);

        appointmentPaymentService.addPayments("appt-uuid", payments);

        verify(appointmentBillingService).addPaymentsForAppointment("appt-uuid", payments);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBlankAppointmentUuid() {
        appointmentPaymentService.addPayments("  ", Collections.emptyList());
    }
}
