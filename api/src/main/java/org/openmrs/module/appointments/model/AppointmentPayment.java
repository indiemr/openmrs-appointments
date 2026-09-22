package org.openmrs.module.appointments.model;

import java.math.BigDecimal;

public class AppointmentPayment {
    private BigDecimal amountPaying;
    private String paymentMode;

    public BigDecimal getAmountPaying() {
        return amountPaying;
    }

    public void setAmountPaying(BigDecimal amountPaying) {
        this.amountPaying = amountPaying;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }
}
