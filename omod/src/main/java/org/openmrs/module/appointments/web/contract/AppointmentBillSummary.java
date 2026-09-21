package org.openmrs.module.appointments.web.contract;

import java.math.BigDecimal;
import java.util.List;

public class AppointmentBillSummary {
    private String uuid;
    private String display;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String status;
    private List<AppointmentPaymentSummary> payments;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<AppointmentPaymentSummary> getPayments() { return payments; }
    public void setPayments(List<AppointmentPaymentSummary> payments) { this.payments = payments; }
}