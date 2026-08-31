package org.openmrs.module.appointments.validator.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.validator.AppointmentValidator;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Rejects a save whose location sits outside the workspaces the caller is assigned to.
 * <p>
 * The location filter guards reads only, so without this check a client can still book INTO another
 * workspace. Entitlement is resolved from the same table the filter reads: visibility comes from the
 * Patient&rarr;Location basis rows, and this comes from the User&rarr;Location rows, so the two can
 * never drift apart.
 * <p>
 * DataFilter is reached reflectively, by design. {@code AccessUtil#getAssignedBasisIds} is static, so
 * the {@code Context.getService} half of the cross-module pattern used by
 * {@link VirtualAppointmentOAuthValidator} does not apply here, and no DataFilter type may appear on
 * an appointments code path.
 */
public class AppointmentLocationEntitlementValidator implements AppointmentValidator {

    private static final Log log = LogFactory.getLog(AppointmentLocationEntitlementValidator.class);

    private static final String DATAFILTER_MODULE_ID = "datafilter";

    private static final String APPOINTMENT_LOCATION_FILTER = "datafilter_locationBasedAppointmentFilter";

    private static final String FILTER_DISABLED_GP = APPOINTMENT_LOCATION_FILTER + ".disabled";

    /** Mirrors DataFilterConstants.BYPASS_PRIV_SUFFIX; see the skip ladder in validate(). */
    private static final String BYPASS_PRIVILEGE = APPOINTMENT_LOCATION_FILTER + "_ByPass";

    private static final String ACCESS_UTIL_CLASS = "org.openmrs.module.datafilter.impl.AccessUtil";

    private static final String ASSIGNED_BASIS_IDS_METHOD = "getAssignedBasisIds";

    private static final String NOT_ENTITLED = "Appointment location is outside the workspaces you have access to";

    private static volatile boolean dataFilterUnavailableLogged = false;

    @Override
    public void validate(Appointment appointment, List<String> errors) {
        if (appointment == null || appointment.getLocation() == null) {
            // A missing location is DefaultAppointmentValidator's error to report, not this one's.
            return;
        }

        // Skip ladder, in the same order DataFilter itself applies it. Anything that skips read
        // filtering must skip entitlement too, or writes end up stricter than reads.
        if (Daemon.isDaemonThread()) {
            return;
        }
        if (Context.isAuthenticated() && Context.getAuthenticatedUser().isSuperUser()) {
            return;
        }
        if (isFilterDisabled()) {
            // Kill-switch parity: flipping the global property off must revert entitlement as well.
            return;
        }
        if (Context.isAuthenticated() && Context.hasPrivilege(BYPASS_PRIVILEGE)) {
            // DataFilter's Util.skipFilter is (GP disabled OR ByPass privilege). Honour both rungs,
            // or a ByPass holder reads unfiltered but writes rejected -- and a basis-less service
            // account, which is the shape the planned CRM credential takes, is rejected outright.
            return;
        }

        if (!Context.isAuthenticated()) {
            errors.add(NOT_ENTITLED);
            return;
        }

        Collection<?> assignedBasisIds;
        try {
            assignedBasisIds = getAssignedLocationBasisIds();
        }
        catch (EntitlementLookupFailure e) {
            // DataFilter IS running, so reads are being filtered. A lookup failure here means this
            // write control is broken, not that it is unnecessary -- a renamed class or a changed
            // signature after a DataFilter upgrade must not silently re-open cross-workspace writes.
            log.error("Appointment location entitlement lookup failed while datafilter is running; "
                    + "refusing the save rather than allowing an unchecked cross-workspace write", e);
            errors.add(NOT_ENTITLED);
            return;
        }
        if (assignedBasisIds == null) {
            // DataFilter absent or not started. There is no visibility filtering either, so
            // entitlement would protect nothing and would couple the two deployments together.
            return;
        }
        if (assignedBasisIds.isEmpty()) {
            // Mirrors DataFilter substituting -1 when a user is assigned no basis: fail closed.
            errors.add(NOT_ENTITLED);
            return;
        }

        Integer locationId = appointment.getLocation().getLocationId();
        if (locationId == null) {
            // Unsaved location; there is no id to compare and the save will fail on its own.
            return;
        }
        if (!containsId(assignedBasisIds, locationId)) {
            errors.add(NOT_ENTITLED);
        }
    }

    /**
     * Read through AdministrationService so the global property cache applies. A missing row means
     * the filter is ENFORCING, matching DataFilter's own Util#isFilterDisabled.
     */
    private boolean isFilterDisabled() {
        String value = Context.getAdministrationService().getGlobalProperty(FILTER_DISABLED_GP);
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /**
     * Raised when DataFilter is started -- so reads ARE filtered -- but its access lookup could not
     * be reached. Distinct from the module being absent, which is a legitimate skip.
     */
    static class EntitlementLookupFailure extends RuntimeException {

        EntitlementLookupFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * @return the location ids the caller is assigned to, already expanded to descendants by
     *         DataFilter, or null when DataFilter is absent or not started.
     * @throws EntitlementLookupFailure when DataFilter is started but the lookup fails; callers must
     *         fail closed rather than treat it as an absent module.
     */
    protected Collection<?> getAssignedLocationBasisIds() {
        Module module = ModuleFactory.getModuleById(DATAFILTER_MODULE_ID);
        if (module == null || !module.isStarted()) {
            logDataFilterUnavailableOnce("module " + DATAFILTER_MODULE_ID + " is absent or not started");
            return null;
        }
        try {
            Object result = invokeAssignedBasisIds(module);
            if (result instanceof Collection) {
                return (Collection<?>) result;
            }
            throw new EntitlementLookupFailure(ASSIGNED_BASIS_IDS_METHOD + " did not return a Collection", null);
        }
        catch (EntitlementLookupFailure e) {
            throw e;
        }
        catch (Exception e) {
            throw new EntitlementLookupFailure(
                    "could not call " + ACCESS_UTIL_CLASS + "." + ASSIGNED_BASIS_IDS_METHOD, e);
        }
    }

    /**
     * The reflective call itself, kept separate so a test can drive the failure this class exists to
     * survive. The realistic trigger is an upstream rename during a DataFilter upgrade, which arrives
     * here as {@link ClassNotFoundException} or {@link NoSuchMethodException} — the conversion in the
     * caller is what turns that into a refusal rather than a silent skip, so that conversion is the
     * thing worth testing.
     */
    protected Object invokeAssignedBasisIds(Module module) throws Exception {
        Class<?> accessUtil = ModuleFactory.getModuleClassLoader(module).loadClass(ACCESS_UTIL_CLASS);
        Method getAssignedBasisIds = accessUtil.getMethod(ASSIGNED_BASIS_IDS_METHOD, Class.class);
        return getAssignedBasisIds.invoke(null, Location.class);
    }

    /**
     * The basis ids come back as strings; a location id is an Integer. Compare as text so the two
     * meet in the same space.
     */
    private boolean containsId(Collection<?> assignedBasisIds, Integer locationId) {
        String target = locationId.toString();
        for (Object basisId : assignedBasisIds) {
            if (basisId != null && target.equals(basisId.toString().trim())) {
                return true;
            }
        }
        return false;
    }

    private void logDataFilterUnavailableOnce(String reason) {
        if (!dataFilterUnavailableLogged) {
            dataFilterUnavailableLogged = true;
            log.warn("Skipping appointment location entitlement checks: " + reason
                    + ". Appointment locations will not be checked against the caller's workspaces.");
        }
    }
}
