package org.openmrs.module.appointments.dao.impl;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.appointments.BaseIntegrationTest;
import org.openmrs.module.appointments.dao.AppointmentDao;
import org.openmrs.module.appointments.dao.AppointmentNoteDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteAuthorType;
import org.openmrs.module.appointments.model.AppointmentNoteType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppointmentNoteDaoImplIT extends BaseIntegrationTest {

    @Autowired
    AppointmentNoteDao appointmentNoteDao;

    @Autowired
    AppointmentDao appointmentDao;

    private Appointment appointment;

    @Before
    public void setUp() throws Exception {
        executeDataSet("appointmentTestData.xml");
        appointment = appointmentDao.getAppointmentByUuid("75504r42-3ca8-11e3-bf2b-0800271c1111");
        assertNotNull(appointment);
    }

    @Test
    public void shouldSaveAndGetNoteByUuid() {
        AppointmentNote saved = appointmentNoteDao.save(note(AppointmentNoteType.REASON_FOR_VISIT, "Fever since Monday"));

        AppointmentNote found = appointmentNoteDao.getByUuid(saved.getUuid());

        assertNotNull(found);
        assertNotNull(found.getId());
        assertEquals("Fever since Monday", found.getNote());
        assertEquals(AppointmentNoteType.REASON_FOR_VISIT, found.getNoteType());
        assertEquals(AppointmentNoteAuthorType.PATIENT, found.getAuthorType());
        assertEquals(appointment, found.getAppointment());
    }

    @Test
    public void shouldGetNotesForAppointmentFilteredByType() {
        appointmentNoteDao.save(note(AppointmentNoteType.REASON_FOR_VISIT, "first"));
        appointmentNoteDao.save(note(AppointmentNoteType.CANCEL, "second"));

        assertEquals(2, appointmentNoteDao.getNotes(appointment, null, false).size());
        List<AppointmentNote> cancelNotes = appointmentNoteDao.getNotes(appointment, AppointmentNoteType.CANCEL, false);
        assertEquals(1, cancelNotes.size());
        assertEquals("second", cancelNotes.get(0).getNote());
    }

    @Test
    public void shouldExcludeVoidedNotesUnlessAsked() {
        AppointmentNote voided = note(AppointmentNoteType.REASON_FOR_VISIT, "wrong");
        voided.setVoided(true);
        appointmentNoteDao.save(voided);
        appointmentNoteDao.save(note(AppointmentNoteType.REASON_FOR_VISIT, "right"));

        List<AppointmentNote> active = appointmentNoteDao.getNotes(appointment, null, false);
        assertEquals(1, active.size());
        assertEquals("right", active.get(0).getNote());
        assertEquals(2, appointmentNoteDao.getNotes(appointment, null, true).size());
    }

    @Test
    public void shouldNotReturnNotesOfOtherAppointments() {
        appointmentNoteDao.save(note(AppointmentNoteType.REASON_FOR_VISIT, "mine"));
        Appointment other = appointmentDao.getAppointmentByUuid("75504r42-3ca8-11e3-bf2b-0800271c12222");

        assertTrue(appointmentNoteDao.getNotes(other, null, false).isEmpty());
    }

    private AppointmentNote note(AppointmentNoteType type, String text) {
        AppointmentNote note = new AppointmentNote();
        note.setAppointment(appointment);
        note.setNoteType(type);
        note.setAuthorType(AppointmentNoteAuthorType.PATIENT);
        note.setNote(text);
        return note;
    }
}
