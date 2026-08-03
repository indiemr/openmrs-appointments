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
    public String createBillForAppointment(String appointmentUuid, boolean createBill) {
        if (!createBill) {
            return null;
        }
        Appointment appointment = appointmentDao.getAppointmentByUuid(appointmentUuid);
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment not found: " + appointmentUuid);
        }
        if (StringUtils.isNotBlank(appointment.getBillUuid())) {
            return appointment.getBillUuid();
        }

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
    public void voidBillForAppointment(String appointmentUuid, String voidReason) {
        if (StringUtils.isBlank(appointmentUuid) || StringUtils.isBlank(voidReason)) {
            return;
        }
        Appointment appointment = appointmentDao.getAppointmentByUuid(appointmentUuid);

        if (appointment == null || StringUtils.isBlank(voidReason) || StringUtils.isBlank(appointment.getBillUuid())) {
            return;
        }


        IBillService billService = Context.getService(IBillService.class);
        Bill bill = billService.getByUuid(appointment.getBillUuid());

        if (bill == null || Boolean.TRUE.equals(bill.getVoided())) {
            return;
        }

        // Skip void: PAID or POSTED bill
        if (BillStatus.PAID.equals(bill.getStatus()) || BillStatus.POSTED.equals(bill.getStatus())) {
            log.warn("Bill " + bill.getUuid() + " is PAID; not voiding on appointment cancel");
            return;
        }

        // Only auto-void PENDING bills
        billService.voidEntity(bill, voidReason);
        log.info("Voided bill " + bill.getUuid() + " for cancelled appointment " + appointment.getUuid());
    }

    @Override
    public String syncBillWithAppointmentService(String appointmentUuid) {
        Appointment appointment = appointmentDao.getAppointmentByUuid(appointmentUuid);
        if (appointment == null || StringUtils.isBlank(appointment.getBillUuid())) {
            return null;
        }
        
        String newBillableServiceUuid = resolveBillableServiceUuid(appointment);
        if (StringUtils.isBlank(newBillableServiceUuid)) {
            return appointment.getBillUuid();
        }

        IBillService billService = Context.getService(IBillService.class);
        Bill oldBill = billService.getByUuid(appointment.getBillUuid());

        // Bill missing or already voided → create fresh bill for current service
        if (oldBill == null || Boolean.TRUE.equals(oldBill.getVoided())) {
            clearBillUuidFromAppointment(appointmentUuid);
            return createBillForAppointment(appointmentUuid, true);
        }

        String oldBillableServiceUuid = resolveBilledServiceUuid(oldBill);

        log.info("oldBillableServiceUuid : " + oldBillableServiceUuid);
        log.info("newBillableServiceUuid : " + newBillableServiceUuid);
        log.info("is same service " + StringUtils.equals(newBillableServiceUuid, oldBillableServiceUuid));
        
        // Same service → nothing to do (slot/time/provider change only)
        if (StringUtils.equals(newBillableServiceUuid, oldBillableServiceUuid)) {
            log.info("Same service, return same bill....");
            return appointment.getBillUuid();
        }

        log.info("diff service, updating bill line item in place....");

        return updateBillLineItemForServiceChange(appointment, oldBill, oldBillableServiceUuid);
        
    }

    private String updateBillLineItemForServiceChange(Appointment appointment, Bill bill, String oldBillableServiceUuid) {
        AppointmentServiceDefinition service = appointment.getService();
        IBillableItemsService billableItemsService = Context.getService(IBillableItemsService.class);
        BillableService newBillableService = billableItemsService.getByUuid(service.getBillableServiceUuid());
        if (newBillableService == null) {
            throw new IllegalArgumentException("Billable service not found: " + service.getBillableServiceUuid());
        }
        BigDecimal newPrice = resolveDefaultPrice(newBillableService);
        if (newPrice == null) {
            throw new IllegalStateException("No price configured for billable service: " + newBillableService.getUuid());
        }
        BillLineItem lineItemToUpdate = findAppointmentLineItem(bill, oldBillableServiceUuid);
        if (lineItemToUpdate == null) {
            throw new IllegalStateException("No matching line item found on bill " + bill.getUuid());
        }

        lineItemToUpdate.setBillableService(newBillableService);
        lineItemToUpdate.setPrice(newPrice);
        lineItemToUpdate.setQuantity(1);

        bill.synchronizeBillStatus();

        IBillService billService = Context.getService(IBillService.class);
        Bill savedBill = billService.save(bill);
        log.info("Updated bill line item on bill " + savedBill.getUuid()
                + " for appointment " + appointment.getUuid()
                + " (old service: " + oldBillableServiceUuid
                + ", new service: " + newBillableService.getUuid() + ")");
        return savedBill.getUuid();
    }

    private BillLineItem findAppointmentLineItem(Bill bill, String oldBillableServiceUuid) {
        List<BillLineItem> lineItems = bill.getLineItems();
        if (lineItems == null || lineItems.isEmpty()) {
            return null;
        }

        // Prefer line item matching the old billed service
        for (BillLineItem lineItem: lineItems) {
            if (lineItem.getBillableService() != null
            && StringUtils.equals(oldBillableServiceUuid, lineItem.getBillableService().getUuid())) {
                return lineItem;
            }
        }

        // Fallback: single line item bill (your current create flow)
        if (bill.getLineItems().size() == 1) {
            return bill.getLineItems().get(0);
        }
        return null;
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

    private String resolveBillableServiceUuid(Appointment appointment) {
        if (appointment == null || appointment.getService() == null) {
            return null;
        }
        return appointment.getService().getBillableServiceUuid();
    }

    private String resolveBilledServiceUuid(Bill bill) {
        if (bill == null || bill.getLineItems() == null) {
            return null;
        }
        return bill.getLineItems().stream()
                .map(BillLineItem::getBillableService)
                .filter(Objects::nonNull)
                .map(BillableService::getUuid)
                .findFirst()
                .orElse(null);
    }

    private void clearBillUuidFromAppointment(String appointmentUuid) {
        Appointment appointment = appointmentDao.getAppointmentByUuid(appointmentUuid);
        if (appointment != null && StringUtils.isNotBlank(appointment.getBillUuid())) {
            appointment.setBillUuid(null);
            appointmentDao.save(appointment);
        }
    }
}
