package org.openmrs.module.appointments.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.appointments.dao.AppointmentDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.service.AppointmentBillingService;
import org.openmrs.module.billing.api.IBillService;
import org.openmrs.module.billing.api.IBillableItemsService;
import org.openmrs.module.billing.api.ICashPointService;
import org.openmrs.module.billing.api.model.Bill;
import org.openmrs.module.billing.api.model.BillLineItem;
import org.openmrs.module.billing.api.model.BillStatus;
import org.openmrs.module.billing.api.model.BillableService;
import org.openmrs.module.billing.api.model.CashPoint;
import org.openmrs.module.billing.api.model.CashierItemPrice;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AppointmentBillingServiceImpl implements AppointmentBillingService {
    private static final Log log = LogFactory.getLog(AppointmentBillingServiceImpl.class);
    private AppointmentDao appointmentDao;

    public void setAppointmentDao(AppointmentDao appointmentDao) {
        this.appointmentDao = appointmentDao;
    }

    @Override
    public String createBillForAppointment(Appointment appointment) {
        if (!Boolean.TRUE.equals(appointment.getCreateBill())) {
            return null;
        }
        if (StringUtils.isNotBlank(appointment.getBillUuid())) {
            return appointment.getBillUuid();
        }
        // if (!isBillingModuleStarted()) {
        //     throw new IllegalStateException("Billing module is not started");
        // }

        AppointmentServiceDefinition service = appointment.getService();
        if (service == null || StringUtils.isBlank(service.getBillableServiceUuid())) {
            throw new IllegalArgumentException("Appointment service has no linked billable service");
        }
        Patient patient = appointment.getPatient();
        if (patient == null) {
            throw new IllegalArgumentException("Appointment has no patient");
        }

        IBillableItemsService billableItemsService = Context.getService(IBillableItemsService.class);
        BillableService billableService = billableItemsService.getByUuid(service.getBillableServiceUuid());
        if (billableService == null) {
            throw new IllegalArgumentException("Billable service not found: " + service.getBillableServiceUuid());
        }
        
        BigDecimal price = resolveDefaultPrice(billableService);
        if (price == null) {
            throw new IllegalStateException("No price configured for billable service: " + billableService.getUuid());
        }

        Provider cashier = resolveCashier(appointment);
        CashPoint cashPoint = resolveCashPoint(appointment.getLocation());
        if (cashier == null) {
            throw new IllegalStateException("Could not resolve cashier for bill creation");
        }
        if (cashPoint == null) {
            throw new IllegalStateException("Could not resolve cash point for bill creation");
        }

        Bill bill = new Bill();
        bill.setPatient(patient);
        bill.setCashier(cashier);
        bill.setCashPoint(cashPoint);
        bill.setStatus(BillStatus.PENDING);

        BillLineItem lineItem = new BillLineItem();
        lineItem.setBillableService(billableService);
        lineItem.setPrice(price);
        lineItem.setQuantity(1);
        lineItem.setPaymentStatus(BillStatus.PENDING);
        lineItem.setLineItemOrder(0);
        bill.addLineItem(lineItem);

        Bill savedBill = Context.getService(IBillService.class).save(bill);
        appointment.setBillUuid(savedBill.getUuid());
        appointmentDao.save(appointment);
        log.info("Created bill " + savedBill.getUuid() + " for appointment " + appointment.getUuid());
        return savedBill.getUuid();
    }

    @Override
    public void voidBillForAppointment(Appointment appointment, String voidReason) {
        if (appointment == null || StringUtils.isBlank(voidReason) || StringUtils.isBlank(appointment.getBillUuid())) {
            return;
        }

        try {
        IBillService billService = Context.getService(IBillService.class);
        Bill bill = billService.getByUuid(appointment.getBillUuid());

        if (bill == null || Boolean.TRUE.equals(bill.getVoided())) {
            return;
        }

        // Only auto-void unpaid bills
        if (BillStatus.PAID.equals(bill.getStatus())) {
            log.warn("Bill " + bill.getUuid() + " is PAID; not voiding on appointment cancel");
            return;
        }
        billService.voidEntity(bill, voidReason);
        log.info("Voided bill " + bill.getUuid() + " for cancelled appointment " + appointment.getUuid());                
        
        } catch (Exception e) {
            log.error("Failed to void bill for appointment " + appointment.getUuid(), e);
        }
    }

    private boolean isBillingModuleStarted() {
        Module billingModule = ModuleFactory.getModuleById("billing");
        return billingModule != null && billingModule.isStarted();
    }

    private BigDecimal resolveDefaultPrice(BillableService billableService) {
        List<CashierItemPrice> prices = billableService.getServicePrices();
        if (prices == null || prices.isEmpty()) {
            return null;
        }
        return prices.stream().map(price -> price.getPrice()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Provider resolveCashier(Appointment appointment) {
        // 1) Provider linked on the appointment service
        AppointmentServiceDefinition appointmentService = appointment.getService();
        if (appointmentService != null && appointmentService.getProvider() != null) {
            return appointmentService.getProvider();
        }

        // 2) Any non-voided provider on the appointment
        Set<AppointmentProvider> appointmentProviders = appointment.getProviders(); 
        if (appointmentProviders != null) {
            for (AppointmentProvider appointmentProvider : appointmentProviders) {
                if (appointmentProvider != null
                        && !Boolean.TRUE.equals(appointmentProvider.getVoided())
                        && appointmentProvider.getProvider() != null) {
                    return appointmentProvider.getProvider();
                }
            }
        }
        
        ProviderService providerService = Context.getProviderService();
        User user = Context.getAuthenticatedUser();
        // 3) Return logged in provider as cashier
        if (user != null && user.getPerson() != null) {
            Collection<Provider> providers = providerService.getProvidersByPerson(user.getPerson(), false);
            if (providers != null && !providers.isEmpty()) {
                return providers.iterator().next();
            }
        }
        return null;
    }

    private CashPoint resolveCashPoint(Location location) {
        ICashPointService cashPointService = Context.getService(ICashPointService.class);
        if (location != null) {
            List<CashPoint> cashPoints = cashPointService.getCashPointsByLocation(location, false);
            if (cashPoints != null && !cashPoints.isEmpty()) {
                return cashPoints.get(0);
            }
        }
        return null;
    }
}
