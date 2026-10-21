package org.openmrs.module.appointments.web.contract;

public class AppointmentNoteResponse {
    private String uuid;
    private String noteType;
    private String authorType;
    private String providerUuid;
    private String providerName;
    private String note;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getNoteType() { return noteType; }
    public void setNoteType(String noteType) { this.noteType = noteType; }
    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }
    public String getProviderUuid() { return providerUuid; }
    public void setProviderUuid(String providerUuid) { this.providerUuid = providerUuid; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
