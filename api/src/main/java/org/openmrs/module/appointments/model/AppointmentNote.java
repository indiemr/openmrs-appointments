package org.openmrs.module.appointments.model;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Provider;

import java.io.Serializable;

/**
 * Why an appointment was booked, rescheduled or cancelled, written by the patient or by staff.
 * Append-only: a wrong note is voided, never edited. Clinical notes stay in
 * {@link Appointment#getComments()} and visit reasons in {@link Appointment#getReasons()}.
 */
public class AppointmentNote extends BaseOpenmrsData implements Serializable {

    private Integer appointmentNoteId;
    private Appointment appointment;
    private AppointmentNoteType noteType;
    private AppointmentNoteAuthorType authorType;
    private Provider provider;
    private String note;

    @Override
    public Integer getId() {
        return appointmentNoteId;
    }

    @Override
    public void setId(Integer id) {
        this.appointmentNoteId = id;
    }

    public Integer getAppointmentNoteId() { return appointmentNoteId; }
    public void setAppointmentNoteId(Integer appointmentNoteId) { this.appointmentNoteId = appointmentNoteId; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public AppointmentNoteType getNoteType() { return noteType; }
    public void setNoteType(AppointmentNoteType noteType) { this.noteType = noteType; }
    public AppointmentNoteAuthorType getAuthorType() { return authorType; }
    public void setAuthorType(AppointmentNoteAuthorType authorType) { this.authorType = authorType; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
