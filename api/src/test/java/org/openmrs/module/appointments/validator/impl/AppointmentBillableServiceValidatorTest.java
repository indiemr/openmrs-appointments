package org.openmrs.module.appointments.validator.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.Location;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.billing.api.IBillableItemsService;
import org.openmrs.module.billing.api.model.BillableService;
import org.openmrs.module.billing.api.model.BillableServiceStatus;
import org.openmrs.module.billing.api.model.CashierItemPrice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AppointmentBillableServiceValidatorTest {

    @Mock
    private IBillableItemsService billableItemsService;

    private AppointmentBillableServiceValidator validator;
    private List<String> errors;

    @Before
    public void setUp() {
        validator = new AppointmentBillableServiceValidator() {
            @Override
            IBillableItemsService billableItemsService() {
                return billableItemsService;
            }
        };
        errors = new ArrayList<>();
    }

    @Test
    public void shouldSkipWhenNoOverride() {
        validator.validate(appointment(null), errors);
        assertTrue(errors.isEmpty());
        verify(billableItemsService, never()).getByUuid(anyString());
    }

    @Test
    public void shouldNotBlockSaveOfAppointmentWithExistingBill() {
        // e.g. check-in of an appointment whose paid bill line was changed in bill history
        Appointment appointment = appointment(null);
        appointment.setBillUuid("bill");
        appointment.setStatus(AppointmentStatus.CheckedIn);

        validator.validate(appointment, errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void shouldAcceptPricedEnabledOverride() {
        when(billableItemsService.getByUuid("override-bs")).thenReturn(billableService("override-bs", "500"));
        validator.validate(appointment("override-bs"), errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void shouldRejectUnknownOverride() {
        validator.validate(appointment("missing-bs"), errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("Billable service not found"));
    }

    @Test
    public void shouldRejectRetiredOverride() {
        BillableService billableService = billableService("override-bs", "500");
        billableService.setRetired(true);
        when(billableItemsService.getByUuid("override-bs")).thenReturn(billableService);
        validator.validate(appointment("override-bs"), errors);
        assertEquals(1, errors.size());
    }

    @Test
    public void shouldRejectDisabledOverride() {
        BillableService billableService = billableService("override-bs", "500");
        billableService.setServiceStatus(BillableServiceStatus.DISABLED);
        when(billableItemsService.getByUuid("override-bs")).thenReturn(billableService);
        validator.validate(appointment("override-bs"), errors);
        assertEquals(1, errors.size());
    }

    @Test
    public void shouldRejectUnpricedOverride() {
        BillableService billableService = billableService("override-bs", null);
        when(billableItemsService.getByUuid("override-bs")).thenReturn(billableService);
        validator.validate(appointment("override-bs"), errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("No price configured"));
    }

    @Test
    public void shouldRejectOverrideFromAnotherClinic() {
        BillableService billableService = billableService("override-bs", "200");
        billableService.setName("Consultation Fee");
        billableService.setLocation(location("nh-clinic"));
        when(billableItemsService.getByUuid("override-bs")).thenReturn(billableService);
        Appointment appointment = appointment("override-bs");
        appointment.setLocation(location("nysha-clinic"));

        validator.validate(appointment, errors);
        assertEquals(1, errors.size());
        assertEquals("Billable service Consultation Fee not found", errors.get(0).trim());
    }

    @Test
    public void shouldAcceptOverrideFromSameClinic() {
        BillableService billableService = billableService("override-bs", "1000");
        billableService.setLocation(location("nysha-clinic"));
        when(billableItemsService.getByUuid("override-bs")).thenReturn(billableService);
        Appointment appointment = appointment("override-bs");
        appointment.setLocation(location("nysha-clinic"));

        validator.validate(appointment, errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void shouldAcceptOverrideWithoutClinic() {
        when(billableItemsService.getByUuid("override-bs")).thenReturn(billableService("override-bs", "500"));
        Appointment appointment = appointment("override-bs");
        appointment.setLocation(location("nysha-clinic"));

        validator.validate(appointment, errors);
        assertTrue(errors.isEmpty());
    }

    private Location location(String uuid) {
        Location location = new Location();
        location.setUuid(uuid);
        return location;
    }

    private Appointment appointment(String override) {
        Appointment appointment = new Appointment();
        AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
        definition.setBillableServiceUuid("default-bs");
        appointment.setService(definition);
        appointment.setBillableServiceUuid(override);
        appointment.setStatus(AppointmentStatus.Scheduled);
        return appointment;
    }

    private BillableService billableService(String uuid, String price) {
        BillableService billableService = new BillableService();
        billableService.setUuid(uuid);
        billableService.setServiceStatus(BillableServiceStatus.ENABLED);
        billableService.setServicePrices(price == null ? Collections.emptyList()
                : Collections.singletonList(new CashierItemPrice(new BigDecimal(price), "default")));
        return billableService;
    }
}
