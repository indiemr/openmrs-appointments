package org.openmrs.module.appointments.model;

public enum AppointmentServiceMode {
    InClinic("InClinic"),
    TeleConsultation("TeleConsultation");

    private final String value;
    AppointmentServiceMode(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
    
}
