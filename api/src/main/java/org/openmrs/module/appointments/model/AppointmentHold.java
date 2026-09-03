package org.openmrs.module.appointments.model;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;

import java.io.Serializable;
import java.util.Date;

public class AppointmentHold extends BaseOpenmrsData implements Serializable {
    private Integer appointmentHoldId;
    private AppointmentServiceDefinition service;
    private Patient patient;
    private Appointment appointment;
    private Date startDateTime;
    private Date endDateTime;
    private AppointmentHoldStatus status;
    private Date expiresAt;

    @Override
    public Integer getId() {
        return appointmentHoldId;
    }

    @Override
    public void setId(Integer id) {
        this.appointmentHoldId = id;
    }

    public Integer getAppointmentHoldId() { return appointmentHoldId; }
    public void setAppointmentHoldId(Integer appointmentHoldId) { this.appointmentHoldId = appointmentHoldId; }
    public AppointmentServiceDefinition getService() { return service; }
    public void setService(AppointmentServiceDefinition service) { this.service = service; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public Date getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Date startDateTime) { this.startDateTime = startDateTime; }
    public Date getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Date endDateTime) { this.endDateTime = endDateTime; }
    public AppointmentHoldStatus getStatus() { return status; }
    public void setStatus(AppointmentHoldStatus status) { this.status = status; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
}