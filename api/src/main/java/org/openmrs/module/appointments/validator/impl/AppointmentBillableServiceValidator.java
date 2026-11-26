package org.openmrs.module.appointments.validator.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.validator.AppointmentValidator;
import org.openmrs.module.billing.api.IBillableItemsService;
import org.openmrs.module.billing.api.model.BillableService;
import org.openmrs.module.billing.api.model.BillableServiceStatus;
import org.openmrs.module.billing.api.model.CashierItemPrice;

import java.util.List;
import java.util.Objects;

/**
 * Checks the per-appointment billable service override before save, because bill create/sync runs
 * after save in its own transaction and its failures are only logged.
 */
public class AppointmentBillableServiceValidator implements AppointmentValidator {

    @Override
    public void validate(Appointment appointment, List<String> errors) {
        if (appointment == null) {
            return;
        }
        String override = appointment.getBillableServiceUuid();
        if (StringUtils.isNotBlank(override)) {
            validateOverride(override, errors);
        }
    }

    private void validateOverride(String billableServiceUuid, List<String> errors) {
        BillableService billableService = billableItemsService().getByUuid(billableServiceUuid);
        if (billableService == null) {
            errors.add("Billable service not found: " + billableServiceUuid);
            return;
        }
        if (Boolean.TRUE.equals(billableService.getRetired())
                || BillableServiceStatus.DISABLED.equals(billableService.getServiceStatus())) {
            errors.add("Billable service is retired or disabled: " + billableServiceUuid);
            return;
        }
        if (!hasPrice(billableService)) {
            errors.add("No price configured for billable service: " + billableServiceUuid);
        }
    }

    private boolean hasPrice(BillableService billableService) {
        List<CashierItemPrice> prices = billableService.getServicePrices();
        return prices != null && prices.stream().map(CashierItemPrice::getPrice).anyMatch(Objects::nonNull);
    }

    IBillableItemsService billableItemsService() {
        return Context.getService(IBillableItemsService.class);
    }
}
