package org.openmrs.module.appointments.web.controller;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.openmrs.module.appointments.web.mapper.AppointmentSlotAvailabilityMapper;
import org.openmrs.module.appointments.service.AppointmentSlotAvailabilityService;
import static org.junit.Assert.assertNull;
import org.openmrs.module.appointments.web.contract.AppointmentSlotAvailabilityResponse;
import org.springframework.http.HttpStatus;
import org.mockito.Mock;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.util.DateUtil;
import org.openmrs.module.appointments.web.contract.AppointmentServiceFullResponse;
import org.openmrs.module.appointments.web.contract.AppointmentServiceDescription;
import org.openmrs.module.appointments.web.mapper.AppointmentServiceMapper;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.when;


public class AppointmentServiceDefinitionControllerTest {

    @Mock
    private AppointmentServiceDefinitionService appointmentServiceDefinitionService;

    @Mock
    private AppointmentServiceMapper appointmentServiceMapper;

    @Mock
    private AppointmentSlotAvailabilityService appointmentSlotAvailabilityService;

    @Mock
    private AppointmentSlotAvailabilityMapper appointmentSlotAvailabilityMapper;

    @InjectMocks
    private AppointmentServiceController appointmentServiceController;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        initMocks(this);
    }

    @Test
    public void shouldCreateAppointmentService() throws Exception {
        AppointmentServiceDescription appointmentServiceDescription = new AppointmentServiceDescription();
        appointmentServiceDescription.setName("Cardio");
        AppointmentServiceDefinition mappedServicePayload = new AppointmentServiceDefinition();
        when(appointmentServiceMapper.fromDescription(appointmentServiceDescription)).thenReturn(mappedServicePayload);
        when(appointmentServiceDefinitionService.save(mappedServicePayload)).thenReturn(mappedServicePayload);
        AppointmentServiceFullResponse response = new AppointmentServiceFullResponse();
        when(appointmentServiceMapper.constructResponse(mappedServicePayload)).thenReturn(response);

        ResponseEntity<Object> savedAppointmentService = appointmentServiceController.defineAppointmentService(appointmentServiceDescription);

        verify(appointmentServiceMapper, times(1)).fromDescription(appointmentServiceDescription);
        verify(appointmentServiceDefinitionService, times(1)).save(any(AppointmentServiceDefinition.class));
        verify(appointmentServiceMapper, times(1)).constructResponse(mappedServicePayload);
        assertNotNull(savedAppointmentService);
        assertEquals(response, savedAppointmentService.getBody());
        assertEquals(200, savedAppointmentService.getStatusCode().value());
    }

    @Test
    public void shouldThrowExceptionWhenCreateAppointmentServiceWhenEmptyPayloadIsGiven() throws Exception {
        AppointmentServiceDescription appointmentServiceDescription = new AppointmentServiceDescription();

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Appointment Service name should not be null");

        appointmentServiceController.defineAppointmentService(appointmentServiceDescription);
    }

    @Test
    public void shouldValidateTheAppointmentServiceAndThrowAnExceptionWhenThereIsNonVoidedAppointmentServiceWithTheSameName() throws Exception {
        AppointmentServiceDescription appointmentServiceDescription = new AppointmentServiceDescription();
        appointmentServiceDescription.setName("Cardio");
        AppointmentServiceDefinition mappedServicePayload = new AppointmentServiceDefinition();
        when(appointmentServiceMapper.fromDescription(appointmentServiceDescription)).thenReturn(mappedServicePayload);
        when(appointmentServiceDefinitionService.save(mappedServicePayload)).thenThrow(new RuntimeException("The service 'Cardio' is already present"));

        ResponseEntity<Object> appointmentService = appointmentServiceController.defineAppointmentService(appointmentServiceDescription);

        assertNotNull(appointmentService);
        assertEquals(400, appointmentService.getStatusCode().value());
        assertEquals("The service 'Cardio' is already present", ((RuntimeException)appointmentService.getBody()).getMessage());
    }

    @Test
    public void shouldGetAllAppointmentServices() throws Exception {
        AppointmentServiceDefinition appointmentServiceDefinition = new AppointmentServiceDefinition();
        List<AppointmentServiceDefinition> appointmentServiceDefinitionList = new ArrayList<>();
        appointmentServiceDefinitionList.add(appointmentServiceDefinition);
        when(appointmentServiceDefinitionService.getAllAppointmentServices(false)).thenReturn(appointmentServiceDefinitionList);
        
        appointmentServiceController.getAllAppointmentServices();
        verify(appointmentServiceDefinitionService, times(1)).getAllAppointmentServices(false);
        verify(appointmentServiceMapper, times(1)).constructDefaultResponseForServiceList(appointmentServiceDefinitionList);
    }
    
    @Test
    public void shouldGetAppointmentServiceByUUID() throws Exception {
        String uuid = "c36006d4-9fbb-4f20-866b-0ece245615b1";
        AppointmentServiceDefinition appointmentServiceDefinition = new AppointmentServiceDefinition();
        appointmentServiceDefinition.setUuid(uuid);
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid(uuid)).thenReturn(appointmentServiceDefinition);
        
        ResponseEntity<Object> response = appointmentServiceController.getAppointmentServiceByUuid(uuid);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(appointmentServiceDefinitionService, times(1)).getAppointmentServiceByUuid(uuid);
        verify(appointmentServiceMapper, times(1)).constructResponse(appointmentServiceDefinition);
    }

    @Test
    public void shouldReturnNotFoundIfServiceNotFound() throws Exception {
        // An unknown uuid resolves to null. This used to throw, which BaseRestController turned
        // into HTTP 500 plus a stack trace on every such request.
        ResponseEntity<Object> response = appointmentServiceController.getAppointmentServiceByUuid("random");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(appointmentServiceDefinitionService, times(1)).getAppointmentServiceByUuid("random");
    }

    @Test
    public void shouldVoidTheAppointmentService() throws Exception {
        String appointmentServiceUuid = "appointmentServiceUuid";
        String voidReason = "voidReason";
        AppointmentServiceDefinition appointmentServiceDefinition = new AppointmentServiceDefinition();
        appointmentServiceDefinition.setUuid(appointmentServiceUuid);
        appointmentServiceDefinition.setName("serviceName");
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid(appointmentServiceUuid)).thenReturn(appointmentServiceDefinition);
        when(appointmentServiceDefinitionService.voidAppointmentService(appointmentServiceDefinition, voidReason)).thenReturn(appointmentServiceDefinition);
        AppointmentServiceFullResponse appointmentServiceFullResponse = new AppointmentServiceFullResponse();
        when(appointmentServiceMapper.constructResponse(appointmentServiceDefinition)).thenReturn(appointmentServiceFullResponse);

        ResponseEntity<Object> response = appointmentServiceController.voidAppointmentService(appointmentServiceUuid, voidReason);

        verify(appointmentServiceDefinitionService, times(1)).getAppointmentServiceByUuid(appointmentServiceUuid);
        verify(appointmentServiceDefinitionService, times(1)).voidAppointmentService(appointmentServiceDefinition, voidReason);
        verify(appointmentServiceMapper, times(1)).constructResponse(appointmentServiceDefinition);
        assertNotNull(response);
        assertEquals(appointmentServiceFullResponse, response.getBody());
    }

    @Test
    public void shouldVoidTheAppointmentServiceBeIdempotent() throws Exception {
        String appointmentServiceUuid = "appointmentServiceUuid";
        String voidReason = "voidReason";
        AppointmentServiceDefinition appointmentServiceDefinition = new AppointmentServiceDefinition();
        appointmentServiceDefinition.setUuid(appointmentServiceUuid);
        appointmentServiceDefinition.setName("serviceName");
        appointmentServiceDefinition.setVoided(true);
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid(appointmentServiceUuid)).thenReturn(appointmentServiceDefinition);
        when(appointmentServiceDefinitionService.voidAppointmentService(appointmentServiceDefinition, voidReason)).thenReturn(appointmentServiceDefinition);
        AppointmentServiceFullResponse appointmentServiceFullResponse = new AppointmentServiceFullResponse();
        when(appointmentServiceMapper.constructResponse(appointmentServiceDefinition)).thenReturn(appointmentServiceFullResponse);

        ResponseEntity<Object> response = appointmentServiceController.voidAppointmentService(appointmentServiceUuid, voidReason);

        verify(appointmentServiceDefinitionService, times(1)).getAppointmentServiceByUuid(appointmentServiceUuid);
        verify(appointmentServiceDefinitionService, times(0)).voidAppointmentService(appointmentServiceDefinition, voidReason);
        verify(appointmentServiceMapper, times(1)).constructResponse(appointmentServiceDefinition);
        assertNotNull(response);
        assertEquals(appointmentServiceFullResponse, response.getBody());
    }

    @Test
    public void shouldThrowAnExceptionWhenThereAreFutureAppointmentsForTheServiceTryingToDelete() throws Exception {
        String appointmentServiceUuid = "appointmentServiceUuid";
        String voidReason = "voidReason";
        AppointmentServiceDefinition appointmentServiceDefinition = new AppointmentServiceDefinition();
        appointmentServiceDefinition.setUuid(appointmentServiceUuid);
        appointmentServiceDefinition.setName("serviceName");
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid(appointmentServiceUuid)).thenReturn(appointmentServiceDefinition);
        String exceptionMessage = "Please cancel all future appointments for this service to proceed. After deleting this service, you will not be able to see any appointments for it";
        when(appointmentServiceDefinitionService.voidAppointmentService(appointmentServiceDefinition, voidReason)).thenThrow(new RuntimeException(exceptionMessage));

        ResponseEntity<Object> response = appointmentServiceController.voidAppointmentService(appointmentServiceUuid, voidReason);

        verify(appointmentServiceDefinitionService, times(1)).getAppointmentServiceByUuid(appointmentServiceUuid);
        verify(appointmentServiceDefinitionService, times(1)).voidAppointmentService(appointmentServiceDefinition, voidReason);
        verify(appointmentServiceMapper, times(0)).constructResponse(appointmentServiceDefinition);
        assertNotNull(response);
        assertEquals(exceptionMessage, ((RuntimeException)response.getBody()).getMessage());
    }

    @Test
    public void shouldGetLoad() throws Exception {
        String appointmentServiceUuid = "appointmentServiceUuid";
        AppointmentServiceDefinition appointmentServiceDefinition = new AppointmentServiceDefinition();
        appointmentServiceDefinition.setUuid(appointmentServiceUuid);
        appointmentServiceDefinition.setName("serviceName");
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid(appointmentServiceUuid)).thenReturn(appointmentServiceDefinition);
        String startDateString = "2108-08-14T18:30:00.0Z";
        String endDateString = "2108-08-15T18:30:00.0Z";
        Date startDate = DateUtil.convertToLocalDateFromUTC(startDateString);
        Date endDate = DateUtil.convertToLocalDateFromUTC(endDateString);
        when(appointmentServiceDefinitionService.calculateCurrentLoad(appointmentServiceDefinition, startDate, endDate)).thenReturn(new Integer(45));
        Integer response = appointmentServiceController.calculateLoadForService(appointmentServiceUuid, startDateString, endDateString);
        verify(appointmentServiceDefinitionService, times(1)).calculateCurrentLoad(appointmentServiceDefinition, startDate, endDate);
        assertNotNull(response);
        assertEquals(45, response.intValue());
    }

    @Test
    public void shouldGetAppointmentServiceWithSpecialityAndAppointmentServiceTypesInIt() {
        AppointmentServiceDefinition appointmentServiceDefinition = new AppointmentServiceDefinition();
        List<AppointmentServiceDefinition> appointmentServiceDefinitionList = new ArrayList<>();
        appointmentServiceDefinitionList.add(appointmentServiceDefinition);
        when(appointmentServiceDefinitionService.getAllAppointmentServices(false)).thenReturn(appointmentServiceDefinitionList);

        List<AppointmentServiceFullResponse> allAppointmentServicesWithTypes = appointmentServiceController.getAllAppointmentServicesWithTypes();
        verify(appointmentServiceDefinitionService, times(1)).getAllAppointmentServices(false);
        verify(appointmentServiceMapper, times(1)).constructFullResponseForServiceList(appointmentServiceDefinitionList);
    }

    @Test
    public void shouldReturnNotFoundForAvailableSlotsWhenServiceIsNotVisible() throws Exception {
        // An unknown service uuid resolves to null. There was no null guard at all, so the
        // request failed with a 500 from deeper in the stack; it must be a clean 404.
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid("foreignUuid")).thenReturn(null);

        ResponseEntity<List<AppointmentSlotAvailabilityResponse>> response =
                appointmentServiceController.getAvailableSlots("foreignUuid", "2029-03-17", null, null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    public void shouldReturnOkForAvailableSlotsWhenServiceIsVisible() throws Exception {
        AppointmentServiceDefinition service = new AppointmentServiceDefinition();
        service.setUuid("ownUuid");
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid("ownUuid")).thenReturn(service);

        ResponseEntity<List<AppointmentSlotAvailabilityResponse>> response =
                appointmentServiceController.getAvailableSlots("ownUuid", "2029-03-17", null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void shouldReturnNotFoundWhenVoidingAServiceThatIsNotVisible() {
        // The uuid was dereferenced without a null check, so an unknown service produced an
        // NPE and a 500 on a route the frontend uses.
        when(appointmentServiceDefinitionService.getAppointmentServiceByUuid("foreignUuid")).thenReturn(null);

        ResponseEntity<Object> response = appointmentServiceController.voidAppointmentService("foreignUuid", "reason");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }
}
