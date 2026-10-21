package org.openmrs.module.appointments.web.contract;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppointmentNoteRequest {
    // Set when a client echoes back a saved note from a response; such entries are skipped, not re-added
    private String uuid;
    // Optional: each action has a default (create REASON_FOR_VISIT, reschedule RESCHEDULE, cancel CANCEL)
    private String noteType;
    private String authorType;
    private String providerUuid;
    private String note;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getNoteType() { return noteType; }
    public void setNoteType(String noteType) { this.noteType = noteType; }
    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }
    public String getProviderUuid() { return providerUuid; }
    public void setProviderUuid(String providerUuid) { this.providerUuid = providerUuid; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
