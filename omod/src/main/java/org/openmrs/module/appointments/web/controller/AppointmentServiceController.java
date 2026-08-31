package org.openmrs.module.appointments.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceSearchParams;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.service.AppointmentSlotAvailabilityService;
import org.openmrs.module.appointments.util.DateUtil;
import org.openmrs.module.appointments.web.contract.AppointmentServiceDefaultResponse;
import org.openmrs.module.appointments.web.contract.AppointmentServiceDescription;
import org.openmrs.module.appointments.web.contract.AppointmentServiceFullResponse;
import org.openmrs.module.appointments.web.contract.AppointmentSlotAvailabilityResponse;
import org.openmrs.module.appointments.web.mapper.AppointmentServiceMapper;
import org.openmrs.module.appointments.web.mapper.AppointmentSlotAvailabilityMapper;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/appointmentService")
public class AppointmentServiceController extends BaseRestController {

    private static final Log log = LogFactory.getLog(AppointmentServiceController.class);

    @Autowired
    private AppointmentServiceDefinitionService appointmentServiceDefinitionService;
    @Autowired
    private AppointmentServiceMapper appointmentServiceMapper;

    @Autowired
    private AppointmentSlotAvailabilityService appointmentSlotAvailabilityService;

    @Autowired
    private AppointmentSlotAvailabilityMapper appointmentSlotAvailabilityMapper;

    @RequestMapping(method = RequestMethod.GET, value = "availableSlots")
    @ResponseBody
    public ResponseEntity<List<AppointmentSlotAvailabilityResponse>> getAvailableSlots
        (@RequestParam("uuid") String serviceUuid,
        @RequestParam("date") String date,
        @RequestParam(value = "excludeAppointmentUuid", required = false) String excludeAppointmentUuid,
        @RequestParam(value = "patientUuid", required = false) String patientUuid) throws ParseException {
        // There was no null guard at all: an unknown service uuid resolved to null and the
        // request failed with a 500 from deeper in the stack.
        AppointmentServiceDefinition appointmentServiceDefinition =
                appointmentServiceDefinitionService.getAppointmentServiceByUuid(serviceUuid);
        if (appointmentServiceDefinition == null) {
            log.warn("Could not identify appointment service with uuid:" + serviceUuid);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date appointmentDate = dateFormat.parse(date);
        return new ResponseEntity<>(appointmentSlotAvailabilityMapper.constructResponse(
                appointmentSlotAvailabilityService.getAvailableSlots(serviceUuid, appointmentDate, excludeAppointmentUuid, patientUuid)
        ), HttpStatus.OK);
    }
    

    @RequestMapping(method = RequestMethod.GET, value = "all/default")
    @ResponseBody
    public List<AppointmentServiceDefaultResponse> getAllAppointmentServices()  {
        List<AppointmentServiceDefinition> appointmentServiceDefinitions = appointmentServiceDefinitionService.getAllAppointmentServices(false);
        List<AppointmentServiceDefaultResponse> response = appointmentServiceMapper.constructDefaultResponseForServiceList(appointmentServiceDefinitions);
        return response;
    }

    @RequestMapping(method = RequestMethod.GET, value = "all/full")
    @ResponseBody
    public List<AppointmentServiceFullResponse> getAllAppointmentServicesWithTypes() {
        List<AppointmentServiceDefinition> appointmentServiceDefinitions = appointmentServiceDefinitionService.getAllAppointmentServices(false);
        List<AppointmentServiceFullResponse> response = appointmentServiceMapper.constructFullResponseForServiceList(appointmentServiceDefinitions);
        return response;
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> getAppointmentServiceByUuid(@RequestParam("uuid") String uuid)  {
        AppointmentServiceDefinition appointmentServiceDefinition = appointmentServiceDefinitionService.getAppointmentServiceByUuid(uuid);
        if(appointmentServiceDefinition == null){
            // Throwing produced HTTP 500 plus a full stack trace on every unknown uuid. Absent and
            // not-visible are deliberately not distinguished.
            log.warn("No appointment service found with uuid: " + uuid);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        AppointmentServiceFullResponse appointmentServiceFullResponse = appointmentServiceMapper.constructResponse(appointmentServiceDefinition);

        return new ResponseEntity<>(appointmentServiceFullResponse, HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.GET, value = "search")
    @ResponseBody
    public List<AppointmentServiceFullResponse> search(AppointmentServiceSearchParams searchParams) {
        List<AppointmentServiceDefinition> appointmentServiceDefinitions = appointmentServiceDefinitionService.search(searchParams);
        return appointmentServiceMapper.constructFullResponseForServiceList(appointmentServiceDefinitions);
    }

    @RequestMapping( method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> defineAppointmentService(@Valid @RequestBody AppointmentServiceDescription appointmentServiceDescription) throws IOException {
        if(appointmentServiceDescription.getName() == null)
            throw new RuntimeException("Appointment Service name should not be null");
        try {
            // Mapping used to sit outside this try, so a mapping failure escaped to
            // BaseRestController and reached the client as HTTP 500 with a stack trace while an
            // identical failure during save returned 400. Both are rejected input; both are 400.
            AppointmentServiceDefinition appointmentServiceDefinition = appointmentServiceMapper.fromDescription(appointmentServiceDescription);
            AppointmentServiceDefinition savedAppointmentServiceDefinition = appointmentServiceDefinitionService.save(appointmentServiceDefinition);
            AppointmentServiceFullResponse appointmentServiceFullResponse = appointmentServiceMapper.constructResponse(savedAppointmentServiceDefinition);
            return new ResponseEntity<>(appointmentServiceFullResponse, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping( method = RequestMethod.DELETE)
    @ResponseBody
    public ResponseEntity<Object> voidAppointmentService(@RequestParam(value = "uuid", required = true) String appointmentServiceUuid, @RequestParam(value = "void_reason", required = false) String voidReason ) {
        AppointmentServiceDefinition appointmentServiceDefinition = appointmentServiceDefinitionService.getAppointmentServiceByUuid(appointmentServiceUuid);
        if (appointmentServiceDefinition == null) {
            // Dereferenced unguarded before this, so an unknown uuid produced an NPE and a 500.
            // Same contract as the other reads: that case is a 404.
            log.warn("Could not identify appointment service with uuid:" + appointmentServiceUuid);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (appointmentServiceDefinition.getVoided()){
            AppointmentServiceFullResponse appointmentServiceFullResponse = appointmentServiceMapper.constructResponse(appointmentServiceDefinition);
            return new ResponseEntity<>(appointmentServiceFullResponse, HttpStatus.OK);
        }
        try {
            AppointmentServiceDefinition appointmentServiceDefinition1 = appointmentServiceDefinitionService.voidAppointmentService(appointmentServiceDefinition, voidReason);
            AppointmentServiceFullResponse appointmentServiceFullResponse = appointmentServiceMapper.constructResponse(appointmentServiceDefinition1);
            return new ResponseEntity<>(appointmentServiceFullResponse, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "load")
    @ResponseBody
    public Integer calculateLoadForService(@RequestParam("uuid") String serviceUuid, @RequestParam(value = "startDateTime") String startDateTime, @RequestParam(value = "endDateTime") String endDateTime)
            throws ParseException {
        AppointmentServiceDefinition appointmentServiceDefinition = appointmentServiceDefinitionService.getAppointmentServiceByUuid(serviceUuid);
        if(appointmentServiceDefinition == null){
            throw new RuntimeException("Appointment Service does not exist");
        }

        return appointmentServiceDefinitionService.calculateCurrentLoad(appointmentServiceDefinition, DateUtil.convertToLocalDateFromUTC(startDateTime), DateUtil.convertToLocalDateFromUTC(endDateTime));
    }
}
