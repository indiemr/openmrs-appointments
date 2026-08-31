package org.openmrs.module.appointments.dao.impl;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the reflective handles {@code AppointmentLocationEntitlementValidator} reaches into DataFilter
 * with, and checks that the filter its kill switch names is one the JSON definitions actually declare.
 * Neither a build nor a startup notices if these literals drift -- the validator converts the failure
 * into a refusal, which is safe but means every cross-workspace write starts being rejected for the
 * wrong reason.
 * <p>
 * {@code filterNamesDeclaredInJson()} also lives in {@code AppointmentFilterNameAgreementTest} -- keep
 * the two in step.
 */
public class AppointmentEntitlementPinningTest {

    private static final String FILTERS_JSON = "/filters/hibernate/appointments.json";

    private static final String VALIDATOR_CLASS =
            "org.openmrs.module.appointments.validator.impl.AppointmentLocationEntitlementValidator";

    private Set<String> filterNamesDeclaredInJson() throws Exception {
        Set<String> names = new LinkedHashSet<>();
        Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"(datafilter_[^\"]+)\"");
        try (InputStream in = getClass().getResourceAsStream(FILTERS_JSON)) {
            assertTrue("filter definitions are missing from the module resources: " + FILTERS_JSON,
                in != null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = namePattern.matcher(line);
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }

    private String constantValue(Class<?> owner, String fieldName) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    @Test
    public void entitlementValidatorShouldGuardTheAppointmentFilterDeclaredInJson() throws Exception {
        Class<?> validator = Class.forName(VALIDATOR_CLASS);

        String guarded = constantValue(validator, "APPOINTMENT_LOCATION_FILTER");
        String disabledGp = constantValue(validator, "FILTER_DISABLED_GP");
        String bypassPrivilege = constantValue(validator, "BYPASS_PRIVILEGE");

        assertTrue("the validator's kill switch names a filter no JSON definition declares: " + guarded,
            filterNamesDeclaredInJson().contains(guarded));
        // DataFilter derives both of these from the filter name, so they must track it exactly.
        assertEquals(guarded + ".disabled", disabledGp);
        assertEquals(guarded + "_ByPass", bypassPrivilege);
    }

    /**
     * These two literals are the reflective handle into DataFilter. Nothing in a build or a startup
     * notices if they drift -- the validator converts the failure into a refusal, which is safe but
     * means every cross-workspace write starts being rejected for the wrong reason. Pinning them
     * catches an accidental edit here; an upstream rename during a DataFilter upgrade still has to be
     * caught by the conversion test in AppointmentLocationEntitlementValidatorTest.
     */
    @Test
    public void shouldPinTheDataFilterAccessorTheValidatorReachesFor() throws Exception {
        Class<?> validator = Class.forName(VALIDATOR_CLASS);

        assertEquals("org.openmrs.module.datafilter.impl.AccessUtil",
            constantValue(validator, "ACCESS_UTIL_CLASS"));
        assertEquals("getAssignedBasisIds", constantValue(validator, "ASSIGNED_BASIS_IDS_METHOD"));
        assertEquals("datafilter", constantValue(validator, "DATAFILTER_MODULE_ID"));
    }
}
