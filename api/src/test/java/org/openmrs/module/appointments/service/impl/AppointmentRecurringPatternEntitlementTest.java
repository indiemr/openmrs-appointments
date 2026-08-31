package org.openmrs.module.appointments.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.appointments.dao.AppointmentRecurringPatternDao;
import org.openmrs.module.appointments.helper.AppointmentServiceHelper;
import org.openmrs.module.appointments.service.AppointmentNumberGeneratorLocator;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentRecurringPattern;
import org.openmrs.module.appointments.validator.AppointmentValidator;
import org.openmrs.module.appointments.validator.impl.AppointmentLocationEntitlementValidator;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Recurring edits validate against editAppointmentValidators
 * ({@code AppointmentRecurringPatternServiceImpl#update}), which is a different list from the one
 * the create path uses. Entitlement has to be on both lists or recurring edits slip past it.
 */
@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Context.class, Daemon.class })
public class AppointmentRecurringPatternEntitlementTest {

    @Mock
    private AppointmentRecurringPatternDao appointmentRecurringPatternDao;

    @Mock
    private AppointmentServiceHelper appointmentServiceHelper;

    @Mock
    private AppointmentNumberGeneratorLocator appointmentNumberGeneratorLocator;

    @Mock
    private AdministrationService administrationService;

    @Mock
    private User user;

    private AppointmentRecurringPatternServiceImpl recurringAppointmentService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Context.class);
        PowerMockito.mockStatic(Daemon.class);

        when(Context.getAdministrationService()).thenReturn(administrationService);
        // No global property row, so the filter is enforcing and entitlement applies.
        when(administrationService.getGlobalProperty(anyString())).thenReturn(null);
        when(Daemon.isDaemonThread()).thenReturn(false);
        when(Context.isAuthenticated()).thenReturn(true);
        when(Context.getAuthenticatedUser()).thenReturn(user);
        when(user.isSuperUser()).thenReturn(false);

        // The caller is assigned locations 19, 20 and 24 only.
        AppointmentLocationEntitlementValidator entitlementValidator = new AppointmentLocationEntitlementValidator() {

            @Override
            protected Collection<?> getAssignedLocationBasisIds() {
                return Arrays.asList("19", "20", "24");
            }
        };

        List<AppointmentValidator> editAppointmentValidators = new ArrayList<>();
        editAppointmentValidators.add(entitlementValidator);

        // Run the real validator chain through the mocked helper, mirroring
        // AppointmentServiceHelper#validate, so this test exercises the wiring rather than a stub.
        doAnswer(new Answer<Void>() {

            @Override
            @SuppressWarnings("unchecked")
            public Void answer(InvocationOnMock invocation) {
                Appointment appointment = (Appointment) invocation.getArguments()[0];
                List<AppointmentValidator> validators = (List<AppointmentValidator>) invocation.getArguments()[1];
                List<String> errors = new ArrayList<>();
                for (AppointmentValidator validator : validators) {
                    validator.validate(appointment, errors);
                }
                if (!errors.isEmpty()) {
                    throw new APIException(String.join("\n", errors));
                }
                return null;
            }
        }).when(appointmentServiceHelper).validate(any(Appointment.class), anyList());

        recurringAppointmentService = new AppointmentRecurringPatternServiceImpl();
        recurringAppointmentService.setAppointmentRecurringPatternDao(appointmentRecurringPatternDao);
        recurringAppointmentService.setAppointmentServiceHelper(appointmentServiceHelper);
        recurringAppointmentService.setAppointmentNumberGeneratorLocator(appointmentNumberGeneratorLocator);
        recurringAppointmentService.setEditAppointmentValidators(editAppointmentValidators);
    }

    private Appointment appointmentAtLocation(Integer locationId) {
        Appointment appointment = new Appointment();
        Location location = new Location();
        location.setLocationId(locationId);
        appointment.setLocation(location);
        return appointment;
    }

    @Test
    public void shouldRejectARecurringEditIntoALocationOutsideTheCallersWorkspaces() {
        Appointment editedAppointment = appointmentAtLocation(7);
        AppointmentRecurringPattern pattern = new AppointmentRecurringPattern();
        pattern.setAppointments(new LinkedHashSet<>(Arrays.asList(editedAppointment)));

        try {
            recurringAppointmentService.update(pattern, editedAppointment);
            fail("Expected a recurring edit into a foreign location to be rejected");
        }
        catch (APIException e) {
            assertTrue(e.getMessage().contains("outside the workspaces you have access to"));
        }
    }

    @Test
    public void shouldAllowARecurringEditWithinTheCallersWorkspaces() {
        Appointment editedAppointment = appointmentAtLocation(24);
        AppointmentRecurringPattern pattern = new AppointmentRecurringPattern();
        pattern.setAppointments(new LinkedHashSet<>(Arrays.asList(editedAppointment)));

        recurringAppointmentService.update(pattern, editedAppointment);
    }

    @Test
    public void shouldRegisterEntitlementOnEveryValidatorList() throws Exception {
        // The test above wires the validator by hand, so it would still pass if the Spring config
        // missed it. That omission is exactly how recurring edits bypassed entitlement, so assert
        // the config directly: two appointmentValidators lists and two editAppointmentValidators.
        StringBuilder context = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/moduleApplicationContext.xml"), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                context.append(line).append('\n');
            }
        }

        int occurrences = context.toString().split(
                Pattern.quote("<ref bean=\"appointmentLocationEntitlementValidator\"/>"), -1).length - 1;

        assertEquals("entitlement must be on both appointmentValidators and both "
                + "editAppointmentValidators lists", 4, occurrences);
    }

    /**
     * The controller's applyForAll=false branch calls a DIFFERENT overload,
     * {@code update(pattern, List<Appointment>)}. That overload used to validate
     * {@code editedAppointment.getRelatedAppointment()} instead of the appointment being
     * persisted, so a single-occurrence edit reached the database without passing ANY edit
     * validator - including this one. Wiring the bean into all four Spring lists did not close
     * it, because the object handed to the validator was the wrong one.
     */
    @Test
    public void shouldRejectASingleOccurrenceEditIntoALocationOutsideTheCallersWorkspaces() {
        Appointment editedAppointment = appointmentAtLocation(7);
        AppointmentRecurringPattern pattern = new AppointmentRecurringPattern();
        pattern.setAppointments(new LinkedHashSet<>(Arrays.asList(editedAppointment)));

        try {
            recurringAppointmentService.update(pattern, Arrays.asList(editedAppointment));
            fail("Expected a single-occurrence edit into a foreign location to be rejected");
        }
        catch (APIException e) {
            assertTrue(e.getMessage().contains("outside the workspaces you have access to"));
        }
    }

    @Test
    public void shouldAllowASingleOccurrenceEditWithinTheCallersWorkspaces() {
        Appointment editedAppointment = appointmentAtLocation(24);
        AppointmentRecurringPattern pattern = new AppointmentRecurringPattern();
        pattern.setAppointments(new LinkedHashSet<>(Arrays.asList(editedAppointment)));

        recurringAppointmentService.update(pattern, Arrays.asList(editedAppointment));
    }

    @Test
    public void shouldStillValidateTheRelatedAppointmentWhenThereIsOne() {
        // The original related-appointment validation is kept, not replaced: a related
        // appointment sitting in a foreign location must still be rejected.
        Appointment editedAppointment = appointmentAtLocation(24);
        editedAppointment.setRelatedAppointment(appointmentAtLocation(7));
        AppointmentRecurringPattern pattern = new AppointmentRecurringPattern();
        pattern.setAppointments(new LinkedHashSet<>(Arrays.asList(editedAppointment)));

        try {
            recurringAppointmentService.update(pattern, Arrays.asList(editedAppointment));
            fail("Expected a foreign related appointment to still be rejected");
        }
        catch (APIException e) {
            assertTrue(e.getMessage().contains("outside the workspaces you have access to"));
        }
    }
}
