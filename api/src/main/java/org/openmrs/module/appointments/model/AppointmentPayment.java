package org.openmrs.module.appointments.model;

import java.math.BigDecimal;
import java.util.Map;

public class AppointmentPayment {
    private BigDecimal amountPaying;
    private String paymentMode;
    // payment mode attribute type uuid -> value
    private Map<String, String> attributes;

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

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
