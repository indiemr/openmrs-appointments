package org.openmrs.module.appointments.service.impl;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.dao.AppointmentNoteDao;
import org.openmrs.module.appointments.exception.AppointmentErrorReason;
import org.openmrs.module.appointments.exception.AppointmentReasonException;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteAuthorType;
import org.openmrs.module.appointments.model.AppointmentNoteType;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
public class AppointmentNoteServiceImplTest {

    @Mock
    private AppointmentNoteDao appointmentNoteDao;

    @Mock
    private ProviderService providerService;

    @InjectMocks
    private AppointmentNoteServiceImpl appointmentNoteService;

    private User user;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mockStatic(Context.class);
        user = new User();
        user.setPerson(new Person());
        when(Context.getAuthenticatedUser()).thenReturn(user);
        when(Context.getProviderService()).thenReturn(providerService);
        when(appointmentNoteDao.save(any(AppointmentNote.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    public void shouldTrimAndSavePatientNote() {
        AppointmentNote note = note(AppointmentNoteAuthorType.PATIENT, "  Fever since Monday  ");

        AppointmentNote saved = appointmentNoteService.addNote(note);

        assertEquals("Fever since Monday", saved.getNote());
        assertNull(saved.getProvider());
        verify(appointmentNoteDao).save(note);
        verify(providerService, never()).getProvidersByPerson(any(Person.class), any(Boolean.class));
    }

    @Test
    public void shouldSetCurrentUsersProviderOnProviderNoteWithoutProvider() {
        Provider provider = new Provider();
        when(providerService.getProvidersByPerson(user.getPerson(), false)).thenReturn(Collections.singletonList(provider));
        AppointmentNote note = note(AppointmentNoteAuthorType.PROVIDER, "Patient called to cancel");

        AppointmentNote saved = appointmentNoteService.addNote(note);

        assertEquals(provider, saved.getProvider());
    }

    @Test
    public void shouldKeepGivenProviderOnProviderNote() {
        Provider given = new Provider();
        AppointmentNote note = note(AppointmentNoteAuthorType.PROVIDER, "Doctor on leave");
        note.setProvider(given);

        AppointmentNote saved = appointmentNoteService.addNote(note);

        assertEquals(given, saved.getProvider());
        verify(providerService, never()).getProvidersByPerson(any(Person.class), any(Boolean.class));
    }

    @Test
    public void shouldSaveProviderNoteWithoutProviderWhenUserIsNotAProvider() {
        when(providerService.getProvidersByPerson(user.getPerson(), false)).thenReturn(Collections.emptyList());
        AppointmentNote note = note(AppointmentNoteAuthorType.PROVIDER, "Front desk cancelled");

        AppointmentNote saved = appointmentNoteService.addNote(note);

        assertNull(saved.getProvider());
        verify(appointmentNoteDao).save(note);
    }

    @Test
    public void shouldRejectBlankNote() {
        assertRejected(note(AppointmentNoteAuthorType.PATIENT, "   "), AppointmentErrorReason.NOTE_REQUIRED);
    }

    @Test
    public void shouldRejectNoteLongerThanLimit() {
        String text = StringUtils.repeat("a", AppointmentNoteServiceImpl.MAX_NOTE_LENGTH + 1);
        assertRejected(note(AppointmentNoteAuthorType.PATIENT, text), AppointmentErrorReason.NOTE_TOO_LONG);
    }

    @Test
    public void shouldAcceptNoteAtLimit() {
        String text = StringUtils.repeat("a", AppointmentNoteServiceImpl.MAX_NOTE_LENGTH);
        AppointmentNote saved = appointmentNoteService.addNote(note(AppointmentNoteAuthorType.PATIENT, text));
        assertEquals(AppointmentNoteServiceImpl.MAX_NOTE_LENGTH, saved.getNote().length());
    }

    @Test
    public void shouldDefaultAuthorTypeToPatient() {
        AppointmentNote saved = appointmentNoteService.addNote(note(null, "text"));

        assertEquals(AppointmentNoteAuthorType.PATIENT, saved.getAuthorType());
        assertNull(saved.getProvider());
        verify(providerService, never()).getProvidersByPerson(any(Person.class), any(Boolean.class));
    }

    @Test
    public void shouldRejectNoteWithoutType() {
        AppointmentNote note = note(AppointmentNoteAuthorType.PATIENT, "text");
        note.setNoteType(null);
        assertRejected(note, AppointmentErrorReason.INVALID_REQUEST);
    }

    @Test
    public void shouldRejectNoteWithoutAppointment() {
        AppointmentNote note = note(AppointmentNoteAuthorType.PATIENT, "text");
        note.setAppointment(null);
        assertRejected(note, AppointmentErrorReason.INVALID_REQUEST);
    }

    @Test
    public void shouldVoidNote() {
        AppointmentNote note = note(AppointmentNoteAuthorType.PATIENT, "text");
        when(appointmentNoteDao.getByUuid("note-uuid")).thenReturn(note);

        AppointmentNote voided = appointmentNoteService.voidNote("note-uuid", " Entered by mistake ");

        assertTrue(voided.getVoided());
        assertEquals(user, voided.getVoidedBy());
        assertNotNull(voided.getDateVoided());
        assertEquals("Entered by mistake", voided.getVoidReason());
    }

    @Test
    public void shouldUseDefaultVoidReasonWhenBlank() {
        AppointmentNote note = note(AppointmentNoteAuthorType.PATIENT, "text");
        when(appointmentNoteDao.getByUuid("note-uuid")).thenReturn(note);

        assertEquals("Voided", appointmentNoteService.voidNote("note-uuid", null).getVoidReason());
    }

    @Test
    public void shouldRejectVoidOfUnknownNote() {
        try {
            appointmentNoteService.voidNote("missing", "reason");
            fail("Expected AppointmentReasonException");
        } catch (AppointmentReasonException e) {
            assertEquals(AppointmentErrorReason.NOTE_NOT_FOUND, e.getReason());
        }
    }

    private AppointmentNote note(AppointmentNoteAuthorType authorType, String text) {
        AppointmentNote note = new AppointmentNote();
        note.setAppointment(new Appointment());
        note.setNoteType(AppointmentNoteType.REASON_FOR_VISIT);
        note.setAuthorType(authorType);
        note.setNote(text);
        return note;
    }

    private void assertRejected(AppointmentNote note, AppointmentErrorReason reason) {
        try {
            appointmentNoteService.addNote(note);
            fail("Expected AppointmentReasonException");
        } catch (AppointmentReasonException e) {
            assertEquals(reason, e.getReason());
        }
        verify(appointmentNoteDao, never()).save(any(AppointmentNote.class));
    }
}
