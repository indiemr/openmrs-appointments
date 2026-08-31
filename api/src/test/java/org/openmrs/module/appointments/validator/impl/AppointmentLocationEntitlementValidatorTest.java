package org.openmrs.module.appointments.validator.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.appointments.model.Appointment;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Context.class, Daemon.class, org.openmrs.module.ModuleFactory.class })
public class AppointmentLocationEntitlementValidatorTest {

    private static final String FILTER_DISABLED_GP = "datafilter_locationBasedAppointmentFilter.disabled";

    @Mock
    private AdministrationService administrationService;

    @Mock
    private User user;

    private StubbedValidator validator;

    private List<String> errors;

    /**
     * Stubs out only the reflective DataFilter lookup, so these tests exercise the skip ladder and
     * the containment check rather than the cross-module plumbing.
     */
    private static class StubbedValidator extends AppointmentLocationEntitlementValidator {

        private Collection<?> assignedBasisIds;

        private boolean basisLookupCalled;

        private EntitlementLookupFailure lookupFailure;

        @Override
        protected Collection<?> getAssignedLocationBasisIds() {
            basisLookupCalled = true;
            if (lookupFailure != null) {
                throw lookupFailure;
            }
            return assignedBasisIds;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Context.class);
        PowerMockito.mockStatic(Daemon.class);

        when(Context.getAdministrationService()).thenReturn(administrationService);
        // No global property row at all, which means the filter is ENFORCING.
        when(administrationService.getGlobalProperty(anyString())).thenReturn(null);
        when(Daemon.isDaemonThread()).thenReturn(false);
        when(Context.isAuthenticated()).thenReturn(true);
        when(Context.getAuthenticatedUser()).thenReturn(user);
        when(user.isSuperUser()).thenReturn(false);

        validator = new StubbedValidator();
        errors = new ArrayList<>();
    }

    private Appointment appointmentAtLocation(Integer locationId) {
        Appointment appointment = new Appointment();
        Location location = new Location();
        location.setLocationId(locationId);
        appointment.setLocation(location);
        return appointment;
    }

    @Test
    public void shouldPassForASuperUserEvenWithNoAssignedBases() {
        when(user.isSuperUser()).thenReturn(true);
        validator.assignedBasisIds = Collections.emptyList();

        validator.validate(appointmentAtLocation(7), errors);

        assertTrue(errors.isEmpty());
        assertFalse("a super user must skip before the basis lookup", validator.basisLookupCalled);
    }

    @Test
    public void shouldPassOnADaemonThread() {
        when(Daemon.isDaemonThread()).thenReturn(true);
        validator.assignedBasisIds = Collections.emptyList();

        validator.validate(appointmentAtLocation(7), errors);

        assertTrue(errors.isEmpty());
        assertFalse("a daemon thread must skip before the basis lookup", validator.basisLookupCalled);
    }

    @Test
    public void shouldPassWhenTheFilterIsDisabledByGlobalProperty() {
        when(administrationService.getGlobalProperty(FILTER_DISABLED_GP)).thenReturn("true");
        validator.assignedBasisIds = Collections.emptyList();

        validator.validate(appointmentAtLocation(7), errors);

        assertTrue("the global property is the kill switch for entitlement too", errors.isEmpty());
        assertFalse(validator.basisLookupCalled);
    }

    @Test
    public void shouldSkipForAHolderOfTheFiltersByPassPrivilege() {
        // DataFilter's Util.skipFilter is (GP disabled OR ByPass privilege). An assigned-no-basis
        // caller is otherwise rejected, so an empty list proves the skip happened on the privilege
        // rather than on the containment check. Without this rung a ByPass holder -- the shape the
        // planned CRM service credential takes -- reads everything and has every write refused.
        when(Context.hasPrivilege("datafilter_locationBasedAppointmentFilter_ByPass")).thenReturn(true);
        validator.assignedBasisIds = Collections.emptyList();

        validator.validate(appointmentAtLocation(7), errors);

        assertTrue(errors.isEmpty());
        assertFalse("a ByPass holder must skip before the basis lookup", validator.basisLookupCalled);
    }

    @Test
    public void shouldRejectALocationOutsideTheCallersWorkspaces() {
        validator.assignedBasisIds = Arrays.asList("19", "20", "24");

        validator.validate(appointmentAtLocation(7), errors);

        assertEquals(1, errors.size());
        assertEquals("Appointment location is outside the workspaces you have access to", errors.get(0));
    }

    @Test
    public void shouldPassForALocationInsideTheCallersWorkspaces() {
        validator.assignedBasisIds = Arrays.asList("19", "20", "24");

        validator.validate(appointmentAtLocation(24), errors);

        assertTrue(errors.isEmpty());
    }

    @Test
    public void shouldTreatAMissingGlobalPropertyRowAsEnforcing() {
        when(administrationService.getGlobalProperty(FILTER_DISABLED_GP)).thenReturn(null);
        validator.assignedBasisIds = Arrays.asList("19");

        validator.validate(appointmentAtLocation(7), errors);

        assertEquals("a missing row means enforcing, not disabled", 1, errors.size());
    }

    @Test
    public void shouldRejectWhenTheCallerIsAssignedNoBases() {
        validator.assignedBasisIds = Collections.emptyList();

        validator.validate(appointmentAtLocation(7), errors);

        assertEquals(1, errors.size());
    }

    @Test
    public void shouldRejectWhenThereIsNoAuthenticatedUser() {
        when(Context.isAuthenticated()).thenReturn(false);
        validator.assignedBasisIds = Arrays.asList("7");

        validator.validate(appointmentAtLocation(7), errors);

        assertEquals(1, errors.size());
    }

    @Test
    public void shouldSkipEntitlementWhenDataFilterIsUnavailable() {
        // Null stands for "DataFilter absent or not started". Reads are unfiltered in that case, so
        // tightening writes would protect nothing and would couple the two deployments.
        validator.assignedBasisIds = null;

        validator.validate(appointmentAtLocation(7), errors);

        assertTrue(errors.isEmpty());
        // Without this the test passes even if validate() were an empty method, on the one path
        // where a silent skip means cross-workspace writes are allowed.
        assertTrue("the skip must be the result of an attempted lookup, not of never looking",
            validator.basisLookupCalled);
    }

    @Test
    public void shouldRejectWhenDataFilterIsRunningButItsLookupFails() {
        // Module started means reads ARE filtered, so a lookup failure is a broken write control
        // rather than an unnecessary one. Failing open here would silently re-open cross-workspace
        // writes on a renamed class or a changed signature, with every test still green.
        validator.lookupFailure = new AppointmentLocationEntitlementValidator.EntitlementLookupFailure(
            "AccessUtil.getAssignedBasisIds could not be called", null);

        validator.validate(appointmentAtLocation(7), errors);

        assertEquals(1, errors.size());
        assertTrue(validator.basisLookupCalled);
    }

    /**
     * Exercises the real {@code getAssignedLocationBasisIds()} body — the producer — rather than a
     * stub standing in for it. Everything above drives the consumer, which was never the defect:
     * pass-2 #1 was the conversion in this method turning a reflective failure into a refusal. Revert
     * that conversion to {@code return null} and only this class of test notices.
     */
    private static class ReflectionFailingValidator extends AppointmentLocationEntitlementValidator {

        private final Exception toThrow;

        private boolean seamCalled;

        private ReflectionFailingValidator(Exception toThrow) {
            this.toThrow = toThrow;
        }

        @Override
        protected Object invokeAssignedBasisIds(org.openmrs.module.Module module) throws Exception {
            seamCalled = true;
            throw toThrow;
        }
    }

    private void assertRealLookupRefusesOn(Exception reflectionFailure) {
        // Module is final, so it cannot be mocked; isStarted() delegates to the static
        // ModuleFactory.isModuleStarted, which can be.
        org.openmrs.module.Module started = new org.openmrs.module.Module("datafilter");
        PowerMockito.mockStatic(org.openmrs.module.ModuleFactory.class);
        when(org.openmrs.module.ModuleFactory.getModuleById("datafilter")).thenReturn(started);
        when(org.openmrs.module.ModuleFactory.isModuleStarted(started)).thenReturn(true);

        List<String> collected = new ArrayList<>();
        new ReflectionFailingValidator(reflectionFailure).validate(appointmentAtLocation(7), collected);

        // Assert the message, not the count: a rung added above the lookup would keep a count
        // assertion passing for an entirely different reason.
        assertEquals("a reflective failure against a started datafilter must refuse the save",
            Collections.singletonList("Appointment location is outside the workspaces you have access to"),
            collected);
    }

    @Test
    public void shouldSkipWithoutReachingTheLookupWhenTheModuleIsAbsent() {
        // The other half of the split, and the deployment story it protects: with datafilter not
        // installed, reads are unfiltered too, so refusing every appointment write would protect
        // nothing and would couple the two deployments. Exercises the real
        // `module == null -> return null` branch rather than a stub standing in for it.
        PowerMockito.mockStatic(org.openmrs.module.ModuleFactory.class);
        when(org.openmrs.module.ModuleFactory.getModuleById("datafilter")).thenReturn(null);

        ReflectionFailingValidator absent = new ReflectionFailingValidator(new IllegalStateException("must not run"));
        List<String> collected = new ArrayList<>();
        absent.validate(appointmentAtLocation(7), collected);

        assertTrue("an absent datafilter must skip entitlement, not refuse the save", collected.isEmpty());
        assertFalse("the module gate must short-circuit before the reflective call", absent.seamCalled);
    }

    @Test
    public void shouldRefuseWhenTheAccessUtilClassCannotBeLoaded() {
        // What an upstream rename looks like at runtime.
        assertRealLookupRefusesOn(new ClassNotFoundException("org.openmrs.module.datafilter.impl.AccessUtil"));
    }

    @Test
    public void shouldRefuseWhenTheAccessUtilMethodSignatureChanged() {
        assertRealLookupRefusesOn(new NoSuchMethodException("getAssignedBasisIds"));
    }

    @Test
    public void shouldRejectOnLookupFailureEvenForALocationTheUserWouldOtherwiseOwn() {
        validator.lookupFailure = new AppointmentLocationEntitlementValidator.EntitlementLookupFailure(
            "boom", new IllegalStateException("boom"));

        validator.validate(appointmentAtLocation(19), errors);

        assertFalse("a failed lookup must not fall through to the containment check", errors.isEmpty());
    }

    @Test
    public void shouldIgnoreAnAppointmentWithNoLocation() {
        validator.assignedBasisIds = Arrays.asList("19");

        validator.validate(new Appointment(), errors);

        assertTrue("a missing location is DefaultAppointmentValidator's error to report", errors.isEmpty());
    }
}
