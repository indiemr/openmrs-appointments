package org.openmrs.module.appointments.events;

public enum AppointmentEventType {
    BAHMNI_APPOINTMENT_CREATED("bahmni-appointment"),
    BAHMNI_APPOINTMENT_UPDATED("bahmni-appointment"),
    BAHMNI_RECURRING_APPOINTMENT_CREATED("bahmni-recurring-appointment"),
    BAHMNI_RECURRING_APPOINTMENT_UPDATED("bahmni-recurring-appointment"),
    BAHMNI_APPOINTMENT_RESCHEDULED("bahmni-appointment-rescheduled");

    private final String topic;
    AppointmentEventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}