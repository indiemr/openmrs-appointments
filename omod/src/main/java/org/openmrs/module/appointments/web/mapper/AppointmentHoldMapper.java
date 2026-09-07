package org.openmrs.module.appointments.web.mapper;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.PatientService;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.web.contract.AppointmentHoldRequest;
import org.openmrs.module.appointments.web.contract.AppointmentHoldResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppointmentHoldMapper {

    @Autowired
    private PatientService patientService;

    @Autowired
    private AppointmentServiceDefinitionService appointmentServiceDefinitionService;

    public AppointmentHold fromRequest(AppointmentHoldRequest request) {
        if (request == null || StringUtils.isBlank(request.getPatientUuid())
                || StringUtils.isBlank(request.getServiceUuid())
                || request.getStartDateTime() == null) {
            throw new APIException("patientUuid, serviceUuid and startDateTime are required");
        }

        Patient patient = patientService.getPatientByUuid(request.getPatientUuid());
        if (patient == null) {
            throw new APIException("Patient not found");
        }
        AppointmentServiceDefinition service = appointmentServiceDefinitionService
                .getAppointmentServiceByUuid(request.getServiceUuid());
        if (service == null || Boolean.TRUE.equals(service.getVoided())) {
            throw new APIException("Appointment service not found");
        }

        AppointmentHold hold = new AppointmentHold();
        hold.setPatient(patient);
        hold.setService(service);
        hold.setStartDateTime(request.getStartDateTime());
        hold.setEndDateTime(request.getEndDateTime());
        return hold;
    }

    public AppointmentHoldResponse constructResponse(AppointmentHold hold) {
        AppointmentHoldResponse response = new AppointmentHoldResponse();
        response.setUuid(hold.getUuid());
        response.setStatus(hold.getStatus() != null ? hold.getStatus().name() : null);
        response.setPatientUuid(hold.getPatient() != null ? hold.getPatient().getUuid() : null);
        response.setServiceUuid(hold.getService() != null ? hold.getService().getUuid() : null);
        response.setStartDateTime(hold.getStartDateTime());
        response.setEndDateTime(hold.getEndDateTime());
        response.setExpiresAt(hold.getExpiresAt());
        if (hold.getExpiresAt() != null) {
            long seconds = (hold.getExpiresAt().getTime() - System.currentTimeMillis()) / 1000L;
            response.setExpiresInSeconds(Math.max(seconds, 0));
        }
        return response;
    }
}