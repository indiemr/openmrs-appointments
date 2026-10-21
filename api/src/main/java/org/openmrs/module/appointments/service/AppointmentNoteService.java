package org.openmrs.module.appointments.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

import static org.openmrs.module.appointments.constants.PrivilegeConstants.MANAGE_APPOINTMENTS;
import static org.openmrs.module.appointments.constants.PrivilegeConstants.MANAGE_OWN_APPOINTMENTS;
import static org.openmrs.module.appointments.constants.PrivilegeConstants.VIEW_APPOINTMENTS;

public interface AppointmentNoteService {

    /**
     * Validates and saves a new note. The text is trimmed and a missing author type defaults to
     * PATIENT. A PROVIDER note without a provider gets the authenticated user's provider, when there is one.
     */
    @Transactional
    @Authorized({MANAGE_APPOINTMENTS, MANAGE_OWN_APPOINTMENTS})
    AppointmentNote addNote(AppointmentNote note);

    /** Non-voided notes, oldest first. {@code noteType} null means all types. */
    @Transactional(readOnly = true)
    @Authorized({VIEW_APPOINTMENTS, MANAGE_APPOINTMENTS})
    List<AppointmentNote> getNotes(Appointment appointment, AppointmentNoteType noteType);

    /** Non-voided notes of all given appointments, oldest first. Used to fill list and search responses. */
    @Transactional(readOnly = true)
    @Authorized({VIEW_APPOINTMENTS, MANAGE_APPOINTMENTS})
    List<AppointmentNote> getNotes(Collection<Appointment> appointments);

    @Transactional(readOnly = true)
    @Authorized({VIEW_APPOINTMENTS, MANAGE_APPOINTMENTS})
    AppointmentNote getNoteByUuid(String uuid);

    /**
     * Replaces the text of a REASON_FOR_VISIT or ADDITIONAL_INFO note: the old note is voided ("Edited") and a new one with the
     * same type, author and provider is saved, so the original text stays as history. Returns the new
     * note, or the existing one unchanged when the text is the same. RESCHEDULE and CANCEL notes
     * record an action already taken and can't be edited.
     */
    @Transactional
    @Authorized({MANAGE_APPOINTMENTS, MANAGE_OWN_APPOINTMENTS})
    AppointmentNote editNote(String uuid, String note);

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    AppointmentNote voidNote(String uuid, String voidReason);
}
