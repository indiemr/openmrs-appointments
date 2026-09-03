package org.openmrs.module.appointments.web.contract;

import java.util.Date;

public class AppointmentHoldRequest {
    private String patientUuid;
    private String serviceUuid;
    private Date startDateTime;
    private Date endDateTime;

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }
    public String getServiceUuid() { return serviceUuid; }
    public void setServiceUuid(String serviceUuid) { this.serviceUuid = serviceUuid; }
    public Date getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Date startDateTime) { this.startDateTime = startDateTime; }
    public Date getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Date endDateTime) { this.endDateTime = endDateTime; }
}