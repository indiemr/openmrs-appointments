package org.openmrs.module.appointments.validator.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.service.impl.AppointmentBillingServiceImpl;
import org.openmrs.module.appointments.validator.AppointmentValidator;
import org.openmrs.module.billing.api.IBillService;
import org.openmrs.module.billing.api.IBillableItemsService;
import org.openmrs.module.billing.api.model.Bill;
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
        if (StringUtils.isNotBlank(override) && !validateOverride(override, errors)) {
            return;
        }
        validateExistingBillNotLocked(appointment, errors);
    }

    private boolean validateOverride(String billableServiceUuid, List<String> errors) {
        BillableService billableService = billableItemsService().getByUuid(billableServiceUuid);
        if (billableService == null) {
            errors.add("Billable service not found: " + billableServiceUuid);
            return false;
        }
        if (Boolean.TRUE.equals(billableService.getRetired())
                || BillableServiceStatus.DISABLED.equals(billableService.getServiceStatus())) {
            errors.add("Billable service is retired or disabled: " + billableServiceUuid);
            return false;
        }
        if (!hasPrice(billableService)) {
            errors.add("No price configured for billable service: " + billableServiceUuid);
            return false;
        }
        return true;
    }

    /**
     * Changing the billable service of a paid or part-paid bill would rewrite a settled bill, so reject it.
     */
    private void validateExistingBillNotLocked(Appointment appointment, List<String> errors) {
        if (StringUtils.isBlank(appointment.getBillUuid())
                || AppointmentStatus.Cancelled.equals(appointment.getStatus())
                || AppointmentStatus.Missed.equals(appointment.getStatus())) {
            return;
        }
        String effective = appointment.resolveEffectiveBillableServiceUuid();
        if (StringUtils.isBlank(effective)) {
            return;
        }
        Bill bill = billService().getByUuid(appointment.getBillUuid());
        if (bill == null || Boolean.TRUE.equals(bill.getVoided())) {
            return;
        }
        String billed = AppointmentBillingServiceImpl.resolveBilledServiceUuid(bill);
        if (!StringUtils.equals(effective, billed) && AppointmentBillingServiceImpl.isBillLocked(bill)) {
            errors.add("Bill " + bill.getUuid() + " is paid or has payments; its billable service cannot be changed");
        }
    }

    private boolean hasPrice(BillableService billableService) {
        List<CashierItemPrice> prices = billableService.getServicePrices();
        return prices != null && prices.stream().map(CashierItemPrice::getPrice).anyMatch(Objects::nonNull);
    }

    IBillableItemsService billableItemsService() {
        return Context.getService(IBillableItemsService.class);
    }

    IBillService billService() {
        return Context.getService(IBillService.class);
    }
}
