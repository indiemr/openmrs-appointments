package org.openmrs.module.appointments.dao;

import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteType;

import java.util.Collection;
import java.util.List;

public interface AppointmentNoteDao {

    AppointmentNote save(AppointmentNote note);

    AppointmentNote getByUuid(String uuid);

    /** Oldest first. {@code noteType} null means all types. */
    List<AppointmentNote> getNotes(Appointment appointment, AppointmentNoteType noteType, boolean includeVoided);

    /** Notes of all given appointments in as few queries as possible, oldest first. */
    List<AppointmentNote> getNotes(Collection<Appointment> appointments, boolean includeVoided);
}
