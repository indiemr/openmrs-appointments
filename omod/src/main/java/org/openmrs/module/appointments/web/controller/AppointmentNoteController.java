package org.openmrs.module.appointments.web.controller;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteType;
import org.openmrs.module.appointments.service.AppointmentNoteService;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.openmrs.module.appointments.web.contract.AppointmentNoteRequest;
import org.openmrs.module.appointments.web.mapper.AppointmentNoteMapper;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/appointments/{appointmentUuid}/notes")
public class AppointmentNoteController extends BaseRestController {

    private final Log log = LogFactory.getLog(getClass());

    @Autowired
    private AppointmentsService appointmentsService;

    @Autowired
    private AppointmentNoteService appointmentNoteService;

    @Autowired
    private AppointmentNoteMapper appointmentNoteMapper;

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> getNotes(@PathVariable("appointmentUuid") String appointmentUuid,
                                           @RequestParam(value = "type", required = false) String type) {
        try {
            Appointment appointment = appointmentsService.getAppointmentByUuid(appointmentUuid);
            if (appointment == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            AppointmentNoteType noteType = StringUtils.isBlank(type) ? null
                    : AppointmentNoteType.valueOf(type.trim().toUpperCase());
            List<AppointmentNote> notes = appointmentNoteService.getNotes(appointment, noteType);
            return new ResponseEntity<>(appointmentNoteMapper.constructResponse(notes), HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("Failed to get notes for appointment " + appointmentUuid, e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> addNote(@PathVariable("appointmentUuid") String appointmentUuid,
                                          @RequestBody AppointmentNoteRequest request) {
        try {
            Appointment appointment = appointmentsService.getAppointmentByUuid(appointmentUuid);
            if (appointment == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            AppointmentNote note = appointmentNoteMapper.fromRequest(request);
            if (note != null) {
                note.setAppointment(appointment);
            }
            AppointmentNote saved = appointmentNoteService.addNote(note);
            return new ResponseEntity<>(appointmentNoteMapper.constructResponse(saved), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            log.error("Failed to add note to appointment " + appointmentUuid, e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /** Body: { "note": "new text" }. Returns the replacement note, which has a new uuid. */
    @RequestMapping(method = RequestMethod.PUT, value = "/{noteUuid}")
    @ResponseBody
    public ResponseEntity<Object> editNote(@PathVariable("appointmentUuid") String appointmentUuid,
                                           @PathVariable("noteUuid") String noteUuid,
                                           @RequestBody AppointmentNoteRequest request) {
        try {
            AppointmentNote note = appointmentNoteService.getNoteByUuid(noteUuid);
            if (!belongsTo(note, appointmentUuid) || Boolean.TRUE.equals(note.getVoided())) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            AppointmentNote edited = appointmentNoteService.editNote(noteUuid, request == null ? null : request.getNote());
            return new ResponseEntity<>(appointmentNoteMapper.constructResponse(edited), HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("Failed to edit appointment note " + noteUuid, e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(method = RequestMethod.DELETE, value = "/{noteUuid}")
    @ResponseBody
    public ResponseEntity<Object> voidNote(@PathVariable("appointmentUuid") String appointmentUuid,
                                           @PathVariable("noteUuid") String noteUuid,
                                           @RequestParam(value = "reason", required = false) String reason) {
        try {
            AppointmentNote note = appointmentNoteService.getNoteByUuid(noteUuid);
            if (!belongsTo(note, appointmentUuid)) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            appointmentNoteService.voidNote(noteUuid, reason);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (RuntimeException e) {
            log.error("Failed to void appointment note " + noteUuid, e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean belongsTo(AppointmentNote note, String appointmentUuid) {
        return note != null && note.getAppointment() != null
                && appointmentUuid.equals(note.getAppointment().getUuid());
    }
}
