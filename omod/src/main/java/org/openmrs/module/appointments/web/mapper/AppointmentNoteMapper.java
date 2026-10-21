package org.openmrs.module.appointments.web.mapper;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Provider;
import org.openmrs.api.ProviderService;
import org.openmrs.module.appointments.exception.AppointmentErrorReason;
import org.openmrs.module.appointments.exception.AppointmentReasonException;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteAuthorType;
import org.openmrs.module.appointments.model.AppointmentNoteType;
import org.openmrs.module.appointments.web.contract.AppointmentNoteRequest;
import org.openmrs.module.appointments.web.contract.AppointmentNoteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AppointmentNoteMapper {

    @Autowired
    private ProviderService providerService;

    /**
     * Maps everything except the appointment, which the caller sets. The note type is mapped only
     * when present: cancel and reschedule set it themselves. A missing author type is left null and
     * the service defaults it to PATIENT.
     */
    public AppointmentNote fromRequest(AppointmentNoteRequest request) {
        if (request == null) {
            return null;
        }
        AppointmentNote note = new AppointmentNote();
        if (StringUtils.isNotBlank(request.getNoteType())) {
            note.setNoteType(parseEnum(AppointmentNoteType.class, request.getNoteType(), "noteType"));
        }
        if (StringUtils.isNotBlank(request.getAuthorType())) {
            note.setAuthorType(parseEnum(AppointmentNoteAuthorType.class, request.getAuthorType(), "authorType"));
        }
        if (StringUtils.isNotBlank(request.getProviderUuid())) {
            Provider provider = providerService.getProviderByUuid(request.getProviderUuid());
            if (provider == null) {
                throw new AppointmentReasonException(AppointmentErrorReason.INVALID_REQUEST, "Provider not found");
            }
            note.setProvider(provider);
        }
        note.setNote(request.getNote());
        return note;
    }

    /**
     * Notes to add from a request. Entries with a uuid are already saved (a client echoing back a
     * response) and are skipped, so resending a whole appointment never duplicates its notes.
     */
    public List<AppointmentNote> fromRequests(List<AppointmentNoteRequest> requests) {
        List<AppointmentNote> notes = new ArrayList<>();
        if (requests == null) {
            return notes;
        }
        for (AppointmentNoteRequest request : requests) {
            if (request != null && StringUtils.isBlank(request.getUuid())) {
                notes.add(fromRequest(request));
            }
        }
        return notes;
    }

    /** Same as {@link #fromRequests(List)} for an untyped JSON body, such as status-change's map. */
    public List<AppointmentNote> fromJson(Object notesJson) {
        if (notesJson == null) {
            return new ArrayList<>();
        }
        if (!(notesJson instanceof List)) {
            throw new AppointmentReasonException(AppointmentErrorReason.INVALID_REQUEST, "notes must be an array");
        }
        List<AppointmentNoteRequest> requests = new ArrayList<>();
        for (Object item : (List<?>) notesJson) {
            if (!(item instanceof Map)) {
                throw new AppointmentReasonException(AppointmentErrorReason.INVALID_REQUEST,
                        "each note must be an object");
            }
            Map<?, ?> map = (Map<?, ?>) item;
            AppointmentNoteRequest request = new AppointmentNoteRequest();
            request.setUuid(asString(map.get("uuid")));
            request.setNoteType(asString(map.get("noteType")));
            request.setAuthorType(asString(map.get("authorType")));
            request.setProviderUuid(asString(map.get("providerUuid")));
            request.setNote(asString(map.get("note")));
            requests.add(request);
        }
        return fromRequests(requests);
    }

    public AppointmentNoteResponse constructResponse(AppointmentNote note) {
        AppointmentNoteResponse response = new AppointmentNoteResponse();
        response.setUuid(note.getUuid());
        response.setNoteType(note.getNoteType() != null ? note.getNoteType().name() : null);
        response.setAuthorType(note.getAuthorType() != null ? note.getAuthorType().name() : null);
        if (note.getProvider() != null) {
            response.setProviderUuid(note.getProvider().getUuid());
            response.setProviderName(note.getProvider().getName());
        }
        response.setNote(note.getNote());
        return response;
    }

    public List<AppointmentNoteResponse> constructResponse(List<AppointmentNote> notes) {
        return notes.stream().map(this::constructResponse).collect(Collectors.toList());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new AppointmentReasonException(AppointmentErrorReason.INVALID_REQUEST, field + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppointmentReasonException(AppointmentErrorReason.INVALID_REQUEST, "Invalid " + field + ": " + value);
        }
    }
}
