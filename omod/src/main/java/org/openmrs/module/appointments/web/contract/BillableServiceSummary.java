package org.openmrs.module.appointments.web.contract;

import java.math.BigDecimal;

public class BillableServiceSummary {
    private String uuid;
    private String display;      // BillableService.getName()
    private BigDecimal amount; 

    public String getUuid() {
        return this.uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getDisplay() {
        return this.display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
