package org.openmrs.module.appointments.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.module.appointments.dao.AppointmentDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentPayment;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.billing.api.IBillService;
import org.openmrs.module.billing.api.IBillableItemsService;
import org.openmrs.module.billing.api.ICashPointService;
import org.openmrs.module.billing.api.IPaymentModeService;
import org.openmrs.module.billing.api.model.Bill;
import org.openmrs.module.billing.api.model.BillLineItem;
import org.openmrs.module.billing.api.model.BillStatus;
import org.openmrs.module.billing.api.model.BillableService;
import org.openmrs.module.billing.api.model.CashPoint;
import org.openmrs.module.billing.api.model.CashierItemPrice;
import org.openmrs.module.billing.api.model.PaymentMode;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AppointmentBillingServiceImplTest {

    @Mock
    private AppointmentDao appointmentDao;
    @Mock
    private IBillService billService;
    @Mock
    private IBillableItemsService billableItemsService;
    @Mock
    private ICashPointService cashPointService;
    @Mock
    private IPaymentModeService paymentModeService;
    @Mock
    private BillableService billableService;

    private TestableAppointmentBillingService service;

    @Before
    public void setUp() {
        service = new TestableAppointmentBillingService();
        service.setAppointmentDao(appointmentDao);
        service.billService = billService;
        service.billableItemsService = billableItemsService;
        service.cashPointService = cashPointService;
        service.paymentModeService = paymentModeService;
    }

    @Test
    public void shouldReturnNullWhenCreateBillIsFalse() {
        assertNull(service.createBillForAppointment("uuid", false));
        verify(appointmentDao, never()).getAppointmentByUuid(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenAppointmentMissingOnCreate() {
        when(appointmentDao.getAppointmentByUuid("missing")).thenReturn(null);
        service.createBillForAppointment("missing", true);
    }

    @Test
    public void shouldReturnExistingBillUuid() {
        Appointment appointment = appointmentWithServiceAndPatient();
        appointment.setBillUuid("existing-bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);

        assertEquals("existing-bill", service.createBillForAppointment("appt", true));
        verify(billService, never()).save(any(Bill.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenServiceHasNoBillableService() {
        Appointment appointment = new Appointment();
        appointment.setService(new AppointmentServiceDefinition());
        appointment.setPatient(new Patient());
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);

        service.createBillForAppointment("appt", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenAppointmentHasNoPatient() {
        Appointment appointment = new Appointment();
        AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
        definition.setBillableServiceUuid("bs-uuid");
        appointment.setService(definition);
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);

        service.createBillForAppointment("appt", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenBillableServiceNotFound() {
        Appointment appointment = appointmentWithServiceAndPatient();
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        when(billableItemsService.getByUuid("bs-uuid")).thenReturn(null);

        service.createBillForAppointment("appt", true);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowWhenNoPriceConfigured() {
        Appointment appointment = appointmentWithServiceAndPatient();
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        when(billableItemsService.getByUuid("bs-uuid")).thenReturn(billableService);
        when(billableService.getServicePrices()).thenReturn(Collections.emptyList());

        service.createBillForAppointment("appt", true);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowWhenCashierCannotBeResolved() {
        Appointment appointment = appointmentWithServiceAndPatient();
        appointment.setLocation(new Location());
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        stubPricedBillableService();
        when(cashPointService.getCashPointsByLocation(any(Location.class), eq(false)))
                .thenReturn(Collections.singletonList(new CashPoint()));

        service.createBillForAppointment("appt", true);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowWhenCashPointCannotBeResolved() {
        Appointment appointment = appointmentWithServiceAndPatient();
        appointment.getService().setProvider(new Provider());
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        stubPricedBillableService();
        when(cashPointService.getCashPointsByLocation(any(Location.class), anyBoolean()))
                .thenReturn(Collections.emptyList());

        service.createBillForAppointment("appt", true);
    }

    @Test
    public void shouldCreatePendingBillAndStoreUuid() {
        Appointment appointment = appointmentWithServiceAndPatient();
        appointment.setUuid("appt");
        appointment.setLocation(new Location());
        appointment.getService().setProvider(new Provider());
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        stubPricedBillableService();
        when(cashPointService.getCashPointsByLocation(any(Location.class), eq(false)))
                .thenReturn(Collections.singletonList(new CashPoint()));
        when(billService.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill bill = (Bill) invocation.getArguments()[0];
            bill.setUuid("new-bill");
            return bill;
        });

        assertEquals("new-bill", service.createBillForAppointment("appt", true));
        assertEquals("new-bill", appointment.getBillUuid());
        verify(appointmentDao).save(appointment);
        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billService).save(captor.capture());
        assertEquals(BillStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    public void shouldResolveCashierFromAppointmentProviders() {
        Appointment appointment = appointmentWithServiceAndPatient();
        appointment.setUuid("appt");
        appointment.setLocation(new Location());
        Provider provider = new Provider();
        AppointmentProvider appointmentProvider = new AppointmentProvider();
        appointmentProvider.setProvider(provider);
        appointment.setProviders(new HashSet<>(Collections.singletonList(appointmentProvider)));
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        stubPricedBillableService();
        when(cashPointService.getCashPointsByLocation(any(Location.class), eq(false)))
                .thenReturn(Collections.singletonList(new CashPoint()));
        when(billService.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill bill = (Bill) invocation.getArguments()[0];
            bill.setUuid("bill-from-provider");
            return bill;
        });

        assertEquals("bill-from-provider", service.createBillForAppointment("appt", true));
    }

    @Test
    public void shouldSkipAddPaymentsWhenListEmpty() {
        service.addPaymentsForAppointment("appt", Collections.emptyList());
        service.addPaymentsForAppointment("appt", null);
        verify(appointmentDao, never()).getAppointmentByUuid(any());
    }

    @Test
    public void shouldSkipAddPaymentsWhenAppointmentHasNoBill() {
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(new Appointment());
        service.addPaymentsForAppointment("appt", Collections.singletonList(payment("cash", "10")));
        verify(billService, never()).getByUuid(any());
    }

    @Test
    public void shouldSkipAddPaymentsWhenBillIsVoided() {
        Appointment appointment = new Appointment();
        appointment.setBillUuid("bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        Bill bill = new Bill();
        bill.setVoided(true);
        when(billService.getByUuid("bill")).thenReturn(bill);

        service.addPaymentsForAppointment("appt", Collections.singletonList(payment("cash", "10")));
        verify(billService, never()).save(any(Bill.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenPaymentMissingAmountOrMode() {
        Appointment appointment = new Appointment();
        appointment.setBillUuid("bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        when(billService.getByUuid("bill")).thenReturn(new Bill());

        service.addPaymentsForAppointment("appt", Collections.singletonList(new AppointmentPayment()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenPaymentModeMissing() {
        Appointment appointment = new Appointment();
        appointment.setBillUuid("bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        when(billService.getByUuid("bill")).thenReturn(new Bill());
        when(paymentModeService.getByUuid("cash")).thenReturn(null);

        service.addPaymentsForAppointment("appt", Collections.singletonList(payment("cash", "10")));
    }

    @Test
    public void shouldAddPaymentAndSaveBill() {
        Appointment appointment = new Appointment();
        appointment.setBillUuid("bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        Bill bill = new Bill();
        bill.setUuid("bill");
        when(billService.getByUuid("bill")).thenReturn(bill);
        PaymentMode mode = new PaymentMode();
        when(paymentModeService.getByUuid("cash")).thenReturn(mode);
        when(billService.save(bill)).thenReturn(bill);

        service.addPaymentsForAppointment("appt", Collections.singletonList(payment("cash", "200")));

        verify(billService).save(bill);
        assertEquals(1, bill.getPayments().size());
    }

    @Test
    public void shouldSkipVoidWhenUuidOrReasonBlank() {
        service.voidBillForAppointment(" ", "reason");
        service.voidBillForAppointment("appt", " ");
        verify(appointmentDao, never()).getAppointmentByUuid(any());
    }

    @Test
    public void shouldSkipVoidWhenBillAlreadyPaid() {
        Appointment appointment = new Appointment();
        appointment.setBillUuid("bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        Bill bill = new Bill();
        bill.setStatus(BillStatus.PAID);
        when(billService.getByUuid("bill")).thenReturn(bill);

        service.voidBillForAppointment("appt", "cancelled");
        verify(billService, never()).voidEntity(any(Bill.class), any());
    }

    @Test
    public void shouldVoidPendingBill() {
        Appointment appointment = new Appointment();
        appointment.setUuid("appt");
        appointment.setBillUuid("bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        Bill bill = new Bill();
        bill.setUuid("bill");
        bill.setStatus(BillStatus.PENDING);
        when(billService.getByUuid("bill")).thenReturn(bill);

        service.voidBillForAppointment("appt", "cancelled");
        verify(billService).voidEntity(bill, "cancelled");
    }

    @Test
    public void shouldReturnNullWhenSyncHasNoBill() {
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(new Appointment());
        assertNull(service.syncBillWithAppointmentService("appt"));
    }

    @Test
    public void shouldReturnExistingBillWhenServiceHasNoBillableUuid() {
        Appointment appointment = new Appointment();
        appointment.setBillUuid("bill");
        appointment.setService(new AppointmentServiceDefinition());
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);

        assertEquals("bill", service.syncBillWithAppointmentService("appt"));
    }

    @Test
    public void shouldKeepBillWhenServiceUnchanged() {
        Appointment appointment = appointmentWithServiceAndPatient();
        appointment.setBillUuid("bill");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        Bill bill = billWithLineItem("bs-uuid");
        when(billService.getByUuid("bill")).thenReturn(bill);

        assertEquals("bill", service.syncBillWithAppointmentService("appt"));
        verify(billService, never()).save(any(Bill.class));
    }

    @Test
    public void shouldUpdateLineItemWhenServiceChanges() {
        Appointment appointment = appointmentWithServiceAndPatient();
        appointment.setUuid("appt");
        appointment.setBillUuid("bill");
        appointment.getService().setBillableServiceUuid("new-bs");
        when(appointmentDao.getAppointmentByUuid("appt")).thenReturn(appointment);
        Bill bill = billWithLineItem("old-bs");
        bill.setUuid("bill");
        bill.setPayments(new HashSet<>());
        when(billService.getByUuid("bill")).thenReturn(bill);
        when(billableItemsService.getByUuid("new-bs")).thenReturn(billableService);
        when(billableService.getUuid()).thenReturn("new-bs");
        when(billableService.getServicePrices()).thenReturn(Collections.singletonList(pricedItem("600")));
        when(billService.save(bill)).thenReturn(bill);

        assertEquals("bill", service.syncBillWithAppointmentService("appt"));
        verify(billService).save(bill);
    }

    private Appointment appointmentWithServiceAndPatient() {
        Appointment appointment = new Appointment();
        AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
        definition.setBillableServiceUuid("bs-uuid");
        appointment.setService(definition);
        appointment.setPatient(new Patient());
        return appointment;
    }

    private void stubPricedBillableService() {
        when(billableItemsService.getByUuid("bs-uuid")).thenReturn(billableService);
        when(billableService.getUuid()).thenReturn("bs-uuid");
        when(billableService.getServicePrices()).thenReturn(Collections.singletonList(pricedItem("500")));
    }

    private CashierItemPrice pricedItem(String price) {
        return new CashierItemPrice(new BigDecimal(price), "default");
    }

    private AppointmentPayment payment(String mode, String amount) {
        AppointmentPayment payment = new AppointmentPayment();
        payment.setPaymentMode(mode);
        payment.setAmountPaying(new BigDecimal(amount));
        return payment;
    }

    private Bill billWithLineItem(String billableServiceUuid) {
        Bill bill = new Bill();
        BillableService billed = new BillableService();
        billed.setUuid(billableServiceUuid);
        BillLineItem lineItem = new BillLineItem();
        lineItem.setBillableService(billed);
        bill.setLineItems(Collections.singletonList(lineItem));
        return bill;
    }

    private static class TestableAppointmentBillingService extends AppointmentBillingServiceImpl {
        private IBillService billService;
        private IBillableItemsService billableItemsService;
        private ICashPointService cashPointService;
        private IPaymentModeService paymentModeService;

        @Override
        IBillService billService() {
            return billService;
        }

        @Override
        IBillableItemsService billableItemsService() {
            return billableItemsService;
        }

        @Override
        ICashPointService cashPointService() {
            return cashPointService;
        }

        @Override
        IPaymentModeService paymentModeService() {
            return paymentModeService;
        }

        @Override
        org.openmrs.api.ProviderService providerService() {
            return null;
        }

        @Override
        org.openmrs.User authenticatedUser() {
            return null;
        }
    }
}
