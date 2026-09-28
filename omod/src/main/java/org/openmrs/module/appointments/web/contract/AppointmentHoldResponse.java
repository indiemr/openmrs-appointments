package org.openmrs.module.appointments.web.contract;

import java.util.Date;

public class AppointmentHoldResponse {
    private String uuid;
    private String status;
    private String patientUuid;
    private String serviceUuid;
    private Date startDateTime;
    private Date endDateTime;
    private Date expiresAt;
    private long expiresInSeconds;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }
    public String getServiceUuid() { return serviceUuid; }
    public void setServiceUuid(String serviceUuid) { this.serviceUuid = serviceUuid; }
    public Date getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Date startDateTime) { this.startDateTime = startDateTime; }
    public Date getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Date endDateTime) { this.endDateTime = endDateTime; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}