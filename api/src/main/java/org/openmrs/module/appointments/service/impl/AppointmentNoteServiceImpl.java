package org.openmrs.module.appointments.service.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.dao.AppointmentNoteDao;
import org.openmrs.module.appointments.exception.AppointmentErrorReason;
import org.openmrs.module.appointments.exception.AppointmentReasonException;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteAuthorType;
import org.openmrs.module.appointments.model.AppointmentNoteType;
import org.openmrs.module.appointments.service.AppointmentNoteService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@Transactional
public class AppointmentNoteServiceImpl implements AppointmentNoteService {

    public static final int MAX_NOTE_LENGTH = 1000;

    private AppointmentNoteDao appointmentNoteDao;

    public void setAppointmentNoteDao(AppointmentNoteDao appointmentNoteDao) {
        this.appointmentNoteDao = appointmentNoteDao;
    }

    @Override
    public AppointmentNote addNote(AppointmentNote note) {
        if (note == null || note.getAppointment() == null || note.getNoteType() == null) {
            throw new AppointmentReasonException(AppointmentErrorReason.INVALID_REQUEST,
                    "appointment and noteType are required");
        }
        if (note.getAuthorType() == null) {
            note.setAuthorType(AppointmentNoteAuthorType.PATIENT);
        }
        String text = StringUtils.trimToNull(note.getNote());
        if (text == null) {
            throw new AppointmentReasonException(AppointmentErrorReason.NOTE_REQUIRED, "note is required");
        }
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new AppointmentReasonException(AppointmentErrorReason.NOTE_TOO_LONG,
                    "note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        note.setNote(text);
        if (AppointmentNoteAuthorType.PROVIDER.equals(note.getAuthorType()) && note.getProvider() == null) {
            note.setProvider(currentUserProvider());
        }
        return appointmentNoteDao.save(note);
    }

    @Transactional(readOnly = true)
    @Override
    public List<AppointmentNote> getNotes(Appointment appointment, AppointmentNoteType noteType) {
        return appointmentNoteDao.getNotes(appointment, noteType, false);
    }

    @Transactional(readOnly = true)
    @Override
    public List<AppointmentNote> getNotes(Collection<Appointment> appointments) {
        return appointmentNoteDao.getNotes(appointments, false);
    }

    @Transactional(readOnly = true)
    @Override
    public AppointmentNote getNoteByUuid(String uuid) {
        return appointmentNoteDao.getByUuid(uuid);
    }

    @Override
    public AppointmentNote editNote(String uuid, String text) {
        AppointmentNote existing = appointmentNoteDao.getByUuid(uuid);
        if (existing == null || Boolean.TRUE.equals(existing.getVoided())) {
            throw new AppointmentReasonException(AppointmentErrorReason.NOTE_NOT_FOUND, "Appointment note not found");
        }
        if (!existing.getNoteType().isVisitNote()) {
            throw new AppointmentReasonException(AppointmentErrorReason.NOTE_NOT_EDITABLE,
                    existing.getNoteType() + " notes can't be edited");
        }
        if (existing.getNote().equals(StringUtils.trimToNull(text))) {
            return existing;
        }
        AppointmentNote replacement = new AppointmentNote();
        replacement.setAppointment(existing.getAppointment());
        replacement.setNoteType(existing.getNoteType());
        replacement.setAuthorType(existing.getAuthorType());
        replacement.setProvider(existing.getProvider());
        replacement.setNote(text);
        // Validate and save the new text first, so a rejected edit leaves the old note in place
        AppointmentNote saved = addNote(replacement);
        voidNote(uuid, "Edited");
        return saved;
    }

    @Override
    public AppointmentNote voidNote(String uuid, String voidReason) {
        AppointmentNote note = appointmentNoteDao.getByUuid(uuid);
        if (note == null) {
            throw new AppointmentReasonException(AppointmentErrorReason.NOTE_NOT_FOUND, "Appointment note not found");
        }
        if (Boolean.TRUE.equals(note.getVoided())) {
            return note;
        }
        note.setVoided(true);
        note.setVoidedBy(Context.getAuthenticatedUser());
        note.setDateVoided(new Date());
        note.setVoidReason(StringUtils.isBlank(voidReason) ? "Voided" : voidReason.trim());
        return appointmentNoteDao.save(note);
    }

    private Provider currentUserProvider() {
        User user = Context.getAuthenticatedUser();
        if (user == null || user.getPerson() == null) {
            return null;
        }
        Collection<Provider> providers = Context.getProviderService().getProvidersByPerson(user.getPerson(), false);
        return providers.isEmpty() ? null : providers.iterator().next();
    }
}
