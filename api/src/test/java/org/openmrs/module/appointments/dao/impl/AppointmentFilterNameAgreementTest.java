package org.openmrs.module.appointments.dao.impl;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The three filter names are written out independently in more than one place: the JSON definitions
 * this module ships for DataFilter's classpath scan, and the name constants compiled into the
 * datafilter omod. A silent drift between them does not fail a build or a startup -- it just stops
 * that filter binding, which is the worst failure shape available, so this pins what this module
 * declares.
 * <p>
 * The datafilter omod's own {@code ImplConstants.LOCATION_BASED_FILTER_NAMES} is the second copy and
 * lives in another repository, so agreement with it stays a procedural check.
 * <p>
 * {@code filterNamesDeclaredInJson()} also lives in {@code AppointmentEntitlementPinningTest} on the
 * parked entitlement-validator branch -- keep the two in step.
 */
public class AppointmentFilterNameAgreementTest {

    private static final String FILTERS_JSON = "/filters/hibernate/appointments.json";

    private static final List<String> EXPECTED_NAMES = Arrays.asList(
        "datafilter_locationBasedAppointmentFilter",
        "datafilter_locationBasedAppointmentServiceFilter",
        "datafilter_locationBasedAppointmentUnavailabilityFilter");

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

    @Test
    public void jsonShouldDeclareExactlyTheThreeAppointmentFilters() throws Exception {
        assertEquals(new LinkedHashSet<>(EXPECTED_NAMES), filterNamesDeclaredInJson());
    }
}
