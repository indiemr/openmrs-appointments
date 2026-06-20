package org.openmrs.module.appointments.model;

import java.util.Date;

public class AppointmentSlotAvailability {
    private Date startDateTime;
    private Date endDateTime;
    private Integer capacity;
    private Integer booked;
    private Integer available;

    public Date getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Date startDateTime) { this.startDateTime = startDateTime; }
    public Date getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Date endDateTime) { this.endDateTime = endDateTime; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getBooked() { return booked; }
    public void setBooked(Integer booked) { this.booked = booked; }
    public Integer getAvailable() { return available; }
    public void setAvailable(Integer available) { this.available = available; }
}