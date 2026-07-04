package org.openmrs.module.appointments.web.contract;

import java.math.BigDecimal;

public class AppointmentBillSummary {
    private String uuid;
    private String display;
    private BigDecimal amount;
    private String status;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}