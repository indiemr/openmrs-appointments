package org.openmrs.module.appointments.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.service.AppointmentHoldService;
import org.openmrs.module.appointments.web.contract.AppointmentHoldRequest;
import org.openmrs.module.appointments.web.contract.AppointmentHoldResponse;
import org.openmrs.module.appointments.web.mapper.AppointmentHoldMapper;
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
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/appointmentHold")
public class AppointmentHoldController extends BaseRestController {

    private final Log log = LogFactory.getLog(getClass());

    @Autowired
    private AppointmentHoldService appointmentHoldService;

    @Autowired
    private AppointmentHoldMapper appointmentHoldMapper;

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> lockSlot(@RequestBody AppointmentHoldRequest request) {
        try {
            AppointmentHold hold = appointmentHoldMapper.fromRequest(request);
            AppointmentHold saved = appointmentHoldService.createHold(hold);
            return new ResponseEntity<>(appointmentHoldMapper.constructResponse(saved), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            log.error("Failed to lock appointment slot", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{uuid}")
    @ResponseBody
    public ResponseEntity<Object> getHold(@PathVariable("uuid") String uuid) {
        AppointmentHold hold = appointmentHoldService.getByUuid(uuid);
        if (hold == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(appointmentHoldMapper.constructResponse(hold), HttpStatus.OK);
    }
}