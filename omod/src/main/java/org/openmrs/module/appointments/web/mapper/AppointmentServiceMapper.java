package org.openmrs.module.appointments.web.mapper;

import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.AppointmentServiceAttribute;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceMode;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.model.ServiceWeeklyAvailability;
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.appointments.model.AppointmentServiceAttributeType;
import org.openmrs.module.appointments.service.AppointmentServiceAttributeTypeService;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.service.SpecialityService;
import org.openmrs.module.appointments.util.AppointmentBookingRulesUtil;
import org.openmrs.module.appointments.web.contract.*;
import org.openmrs.module.billing.api.model.BillableService;
import org.openmrs.module.billing.api.IBillableItemsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.openmrs.Provider;
import org.openmrs.api.ProviderService;

import java.math.BigDecimal;
import java.sql.Time;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AppointmentServiceMapper {
    @Autowired
    LocationService locationService;

    @Autowired
    SpecialityService specialityService;

    @Autowired
    AppointmentServiceDefinitionService appointmentServiceDefinitionService;

    @Autowired
    AppointmentServiceAttributeTypeService appointmentServiceAttributeTypeService;

    @Autowired 
    ProviderService providerService;

    private static final Log log = LogFactory.getLog(AppointmentServiceMapper.class);

    public AppointmentServiceDefinition fromDescription(AppointmentServiceDescription appointmentServiceDescription) {
        AppointmentServiceDefinition appointmentServiceDefinition;
        if (!StringUtils.isBlank(appointmentServiceDescription.getUuid())) {
            appointmentServiceDefinition = appointmentServiceDefinitionService.getAppointmentServiceByUuid(appointmentServiceDescription.getUuid());
        }else{
            appointmentServiceDefinition = new AppointmentServiceDefinition();
        }
        appointmentServiceDefinition.setName(appointmentServiceDescription.getName());
        appointmentServiceDefinition.setDescription(appointmentServiceDescription.getDescription());
        appointmentServiceDefinition.setDurationMins(appointmentServiceDescription.getDurationMins());
        appointmentServiceDefinition.setStartTime(appointmentServiceDescription.getStartTime());
        appointmentServiceDefinition.setEndTime(appointmentServiceDescription.getEndTime());
        appointmentServiceDefinition.setMaxAppointmentsLimit(appointmentServiceDescription.getMaxAppointmentsLimit());
        appointmentServiceDefinition.setMaxAppointmentsPerSlot(appointmentServiceDescription.getMaxAppointmentsPerSlot());
        appointmentServiceDefinition.setColor(appointmentServiceDescription.getColor());
        Boolean allowPatientBooking = appointmentServiceDescription.getAllowPatientBooking();
        if (allowPatientBooking != null) {
            appointmentServiceDefinition.setAllowPatientBooking(allowPatientBooking);
        } else if (appointmentServiceDefinition.getAllowPatientBooking() == null) {
            // new service, or existing row never had value loaded
            appointmentServiceDefinition.setAllowPatientBooking(Boolean.TRUE);
        }

        String serviceMode = appointmentServiceDescription.getServiceMode();
        if(StringUtils.isNotBlank(serviceMode)) {
            try {
                appointmentServiceDefinition.setServiceMode(AppointmentServiceMode.valueOf(serviceMode));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("serviceMode must be InClinic or TeleConsultation");
            }
        } else if (appointmentServiceDefinition.getServiceMode() == null) {
            appointmentServiceDefinition.setServiceMode(AppointmentServiceMode.InClinic);
        }

        // on edit: if omitted, keep existing DB value
        String initialAppointmentStatus = appointmentServiceDescription.getInitialAppointmentStatus();
        if (StringUtils.isNotBlank(initialAppointmentStatus)) {
            appointmentServiceDefinition.setInitialAppointmentStatus(AppointmentStatus.valueOf(initialAppointmentStatus));
        }else {
            appointmentServiceDefinition.setInitialAppointmentStatus(null);
        }

        String locationUuid = appointmentServiceDescription.getLocationUuid();
        Location location = locationService.getLocationByUuid(locationUuid);
        appointmentServiceDefinition.setLocation(location);

        String providerUuid = appointmentServiceDescription.getProviderUuid();
        if (StringUtils.isNotBlank(providerUuid)) {
            Provider provider = providerService.getProviderByUuid(providerUuid);
            appointmentServiceDefinition.setProvider(provider);
        } else if (StringUtils.isBlank(appointmentServiceDescription.getUuid())) {
            // new service without provider
            appointmentServiceDefinition.setProvider(null);
        } 
        // else if (appointmentServiceDescription.getProviderUuid() != null && appointmentServiceDescription.getProviderUuid().isEmpty()) {
        //     appointmentServiceDefinition.setProvider(null);
        // }

        String specialityUuid = appointmentServiceDescription.getSpecialityUuid();
        Speciality speciality = specialityService.getSpecialityByUuid(specialityUuid);
        appointmentServiceDefinition.setSpeciality(speciality);

        List<ServiceWeeklyAvailabilityDescription> weeklyAvailabilities = appointmentServiceDescription.getWeeklyAvailability();

        if(weeklyAvailabilities != null) {
            Set<ServiceWeeklyAvailability> availabilityList = weeklyAvailabilities.stream()
                    .map(avb -> constructServiceWeeklyAvailability(avb, appointmentServiceDefinition)).collect(Collectors.toSet());
            appointmentServiceDefinition.setWeeklyAvailability(availabilityList);
        }
        
        if(appointmentServiceDescription.getServiceTypes() != null) {
            appointmentServiceDescription.getServiceTypes()
                    .forEach(serviceType -> constructAppointmentServiceTypes(serviceType, appointmentServiceDefinition));
        }

        if(appointmentServiceDescription.getAttributes() != null) {
            appointmentServiceDescription.getAttributes()
                    .forEach(attribute -> constructAppointmentServiceAttribute(attribute, appointmentServiceDefinition));
        }

        appointmentServiceDefinition.setBillableServiceUuid(appointmentServiceDescription.getBillableServiceUuid());

        validateAttributeCardinality(appointmentServiceDefinition);
        AppointmentBookingRulesUtil.validateBookAheadDays(appointmentServiceDescription.getBookAheadDays());
        appointmentServiceDefinition.setBookAheadDays(appointmentServiceDescription.getBookAheadDays());

        AppointmentBookingRulesUtil.validateLeadTimeMinutes(appointmentServiceDescription.getLeadTimeMinutes());
        appointmentServiceDefinition.setLeadTimeMinutes(appointmentServiceDescription.getLeadTimeMinutes());

        AppointmentBookingRulesUtil.validateCancellationCutoffMinutes(appointmentServiceDescription.getCancellationCutoffMinutes());
    appointmentServiceDefinition.setCancellationCutoffMinutes(appointmentServiceDescription.getCancellationCutoffMinutes());

        return appointmentServiceDefinition;
    }

    private void constructAppointmentServiceTypes(AppointmentServiceTypeDescription ast, AppointmentServiceDefinition appointmentServiceDefinition) {
        AppointmentServiceType serviceType;
        Set<AppointmentServiceType> existingServiceTypes = appointmentServiceDefinition.getServiceTypes(true);
        if(ast.getUuid() != null)
            serviceType = getServiceTypeByUuid(existingServiceTypes, ast.getUuid());
        else
            serviceType = new AppointmentServiceType();
        serviceType.setName(ast.getName());
        serviceType.setDuration(ast.getDuration());
        serviceType.setAppointmentServiceDefinition(appointmentServiceDefinition);
        if (ast.getVoided() != null) {
            setVoidedInfo(serviceType, ast.getVoidedReason());
        }
        existingServiceTypes.add(serviceType);
    }

    private void setVoidedInfo(AppointmentServiceType serviceType, String voidReason) {
        serviceType.setVoided(true);
        serviceType.setVoidReason(voidReason);
        serviceType.setDateVoided(new Date());
        serviceType.setVoidedBy(Context.getAuthenticatedUser());
    }

    private void constructAppointmentServiceAttribute(AppointmentServiceAttributeDescription attrDesc, AppointmentServiceDefinition appointmentServiceDefinition) {
        AppointmentServiceAttribute attribute;
        Set<AppointmentServiceAttribute> existingAttributes = appointmentServiceDefinition.getAttributes(true);

        if(attrDesc.getUuid() != null)
            attribute = getAttributeByUuid(existingAttributes, attrDesc.getUuid());
        else
            attribute = new AppointmentServiceAttribute();

        AppointmentServiceAttributeType attributeType = appointmentServiceAttributeTypeService.getAttributeTypeByUuid(attrDesc.getAttributeTypeUuid());
        if (attributeType == null) {
            throw new RuntimeException("Attribute type not found: " + attrDesc.getAttributeTypeUuid());
        }

        attribute.setAttributeType(attributeType);
        attribute.setValueReferenceInternal(attrDesc.getValue());
        attribute.setAppointmentService(appointmentServiceDefinition);

        if (attrDesc.getVoided() != null && attrDesc.getVoided()) {
            setVoidedInfoForAttribute(attribute, attrDesc.getVoidReason());
        }

        existingAttributes.add(attribute);
    }

    private void setVoidedInfoForAttribute(AppointmentServiceAttribute attribute, String voidReason) {
        attribute.setVoided(true);
        attribute.setVoidReason(voidReason);
        attribute.setDateVoided(new Date());
        attribute.setVoidedBy(Context.getAuthenticatedUser());
    }

    private void validateAttributeCardinality(AppointmentServiceDefinition appointmentServiceDefinition) {
        List<AppointmentServiceAttributeType> allAttributeTypes = appointmentServiceAttributeTypeService.getAllAttributeTypes(false);

        for (AppointmentServiceAttributeType attributeType : allAttributeTypes) {
            Set<AppointmentServiceAttribute> existingAttributes = appointmentServiceDefinition.getAttributes(true);
            long count = existingAttributes.stream()
                    .filter(attr -> !attr.getVoided())
                    .filter(attr -> attr.getAttributeType().getUuid().equals(attributeType.getUuid()))
                    .count();

            Integer minOccurs = attributeType.getMinOccurs();
            if (minOccurs != null && minOccurs > 0) {
                if (count < minOccurs) {
                    throw new RuntimeException("Attribute '" + attributeType.getName() +
                            "' requires at least " + minOccurs + " occurrence(s), but only " + count + " provided");
                }
            }

            Integer maxOccurs = attributeType.getMaxOccurs();
            if (maxOccurs != null && maxOccurs > 0) {
                if (count > maxOccurs) {
                    throw new RuntimeException("Attribute '" + attributeType.getName() +
                            "' allows at most " + maxOccurs + " occurrence(s), but " + count + " provided");
                }
            }
        }
    }

    private AppointmentServiceAttribute getAttributeByUuid(Set<AppointmentServiceAttribute> attributes, String attributeUuid) {
        return attributes.stream()
                .filter(attr -> attr.getUuid().equals(attributeUuid)).findAny().get();
    }

    private ServiceWeeklyAvailability constructServiceWeeklyAvailability(ServiceWeeklyAvailabilityDescription avb, AppointmentServiceDefinition appointmentServiceDefinition) {
        ServiceWeeklyAvailability availability;
        if(avb.getUuid() != null)
            availability = getAvailabilityByUuid(appointmentServiceDefinition.getWeeklyAvailability(), avb.getUuid());
        else
            availability = new ServiceWeeklyAvailability();
        availability.setDayOfWeek(avb.getDayOfWeek());
        availability.setStartTime(avb.getStartTime());
        availability.setEndTime(avb.getEndTime());
        availability.setMaxAppointmentsLimit(avb.getMaxAppointmentsLimit());
        availability.setService(appointmentServiceDefinition);
        availability.setVoided(avb.isVoided());
        availability.setMaxAppointmentsPerSlot(avb.getMaxAppointmentsPerSlot());

        return availability;
    }

    private ServiceWeeklyAvailability getAvailabilityByUuid(Set<ServiceWeeklyAvailability> weeklyAvailabilities, String availabilityUuid) {
           return weeklyAvailabilities.stream()
                    .filter(avb -> avb.getUuid().equals(availabilityUuid)).findAny().get();
    }

    private AppointmentServiceType getServiceTypeByUuid(Set<AppointmentServiceType> serviceTypes, String serviceTypeUuid) {
        return serviceTypes.stream()
                .filter(avb -> avb.getUuid().equals(serviceTypeUuid)).findAny().get();
    }

    public List<AppointmentServiceDefaultResponse> constructDefaultResponseForServiceList(List<AppointmentServiceDefinition> appointmentServiceDefinitions) {
        return appointmentServiceDefinitions.stream().map(as -> this.mapToDefaultResponse(as, new AppointmentServiceDefaultResponse())).collect(Collectors.toList());
    }

    private Map constructServiceTypeResponse(AppointmentServiceType serviceType) {
        Map serviceTypeMap = new HashMap();
        serviceTypeMap.put("name", serviceType.getName());
        serviceTypeMap.put("duration",serviceType.getDuration());
        serviceTypeMap.put("uuid", serviceType.getUuid());
        return serviceTypeMap;
    }

    public List<AppointmentServiceFullResponse> constructFullResponseForServiceList(List<AppointmentServiceDefinition> appointmentServiceDefinitions) {
        return appointmentServiceDefinitions.stream().map(as -> this.constructResponse(as)).collect(Collectors.toList());
    }

    public AppointmentServiceFullResponse constructResponse(AppointmentServiceDefinition service) {
        AppointmentServiceFullResponse response = new AppointmentServiceFullResponse();
        mapToDefaultResponse(service, response);
        Set<ServiceWeeklyAvailability> serviceWeeklyAvailability = service.getWeeklyAvailability();
        if(serviceWeeklyAvailability != null) {
            response.setWeeklyAvailability(serviceWeeklyAvailability.stream().map(availability -> this.constructAvailabilityResponse(availability)).collect(Collectors.toList()));
        }
        Set<AppointmentServiceType> serviceTypes = service.getServiceTypes();
        if(serviceTypes != null) {
            response.setServiceTypes(serviceTypes.stream().map(serviceType -> this.constructServiceTypeResponse(serviceType)).collect(Collectors.toList()));
        }
        return response;
    }

    public AppointmentServiceDefaultResponse constructDefaultResponse(AppointmentServiceDefinition appointmentServiceDefinition){
        return mapToDefaultResponse(appointmentServiceDefinition, new AppointmentServiceDefaultResponse());
    }
    
    private AppointmentServiceDefaultResponse mapToDefaultResponse(AppointmentServiceDefinition as, AppointmentServiceDefaultResponse asResponse) {
        asResponse.setUuid(as.getUuid());
        asResponse.setAppointmentServiceId(as.getAppointmentServiceId());
        asResponse.setName(as.getName());
        asResponse.setStartTime(convertTimeToString(as.getStartTime()));
        asResponse.setEndTime(convertTimeToString(as.getEndTime()));
        asResponse.setDescription(as.getDescription());
        asResponse.setDurationMins(as.getDurationMins());
        asResponse.setMaxAppointmentsLimit(as.getMaxAppointmentsLimit());
        asResponse.setColor(as.getColor());
        asResponse.setMaxAppointmentsPerSlot(as.getMaxAppointmentsPerSlot());
        asResponse.setBookAheadDays(as.getBookAheadDays());
        asResponse.setLeadTimeMinutes(as.getLeadTimeMinutes());
        asResponse.setAllowPatientBooking(as.getAllowPatientBooking());
        asResponse.setCancellationCutoffMinutes(as.getCancellationCutoffMinutes());

        AppointmentStatus initialAppointmentStatus = as.getInitialAppointmentStatus();
        if (null != initialAppointmentStatus){
            asResponse.setInitialAppointmentStatus(initialAppointmentStatus.name());
        }

        AppointmentServiceMode serviceMode = as.getServiceMode();
        if (serviceMode != null) {
            asResponse.setServiceMode(serviceMode.name());
        } else {
            asResponse.setServiceMode(AppointmentServiceMode.InClinic.name());
        }

        Map specialityMap = new HashMap();
        Speciality speciality = as.getSpeciality();
        if(speciality != null) {
            specialityMap.put("name", speciality.getName());
            specialityMap.put("uuid", speciality.getUuid());
        }
        asResponse.setSpeciality(specialityMap);

        Map locationMap = new HashMap();
        Location location = as.getLocation();
        if(location != null) {
            locationMap.put("name", location.getName());
            locationMap.put("uuid", location.getUuid());
        }

        Map providerMap = new HashMap<>();
        Provider provider = as.getProvider();
        if (provider != null) {
            providerMap.put("name", provider.getName());
            providerMap.put("uuid", provider.getUuid());
            asResponse.setProvider(providerMap);
        }
        asResponse.setLocation(locationMap);

        Set<AppointmentServiceAttribute> attributes = as.getAttributes();
        if(attributes != null && !attributes.isEmpty()) {
            List<AppointmentServiceAttributeResponse> attributeResponses = attributes.stream()
                    .filter(attr -> !attr.getVoided())
                    .map(attr -> constructAttributeResponse(attr))
                    .collect(Collectors.toList());
            asResponse.setAttributes(attributeResponses);
        }

        if (StringUtils.isNotBlank(as.getBillableServiceUuid())) {
            BillableServiceSummary summary = resolveBillableServiceSummary(as.getBillableServiceUuid());
            if (summary != null) {
                asResponse.setBillableService(summary);
            }
        }

        return asResponse;
    }

    private Map constructAvailabilityResponse(ServiceWeeklyAvailability availability) {
        Map availabilityMap = new HashMap();
        availabilityMap.put("dayOfWeek",availability.getDayOfWeek());
        availabilityMap.put("startTime", convertTimeToString(availability.getStartTime()));
        availabilityMap.put("endTime", convertTimeToString(availability.getEndTime()));
        availabilityMap.put("maxAppointmentsLimit", availability.getMaxAppointmentsLimit());
        availabilityMap.put("uuid", availability.getUuid());
        availabilityMap.put("maxAppointmentsPerSlot", availability.getMaxAppointmentsPerSlot());
        return availabilityMap;
    }

    private String convertTimeToString(Time time) {
       return time != null ? time.toString() : new String();
    }

    private AppointmentServiceAttributeResponse constructAttributeResponse(AppointmentServiceAttribute attribute) {
        AppointmentServiceAttributeResponse response = new AppointmentServiceAttributeResponse();
        response.setUuid(attribute.getUuid());
        response.setAttributeType(attribute.getAttributeType().getName());
        response.setAttributeTypeUuid(attribute.getAttributeType().getUuid());

        try {
            Object value = attribute.getAttributeType().getDatatypeClassname() != null
                    ? attribute.getValue()
                    : attribute.getValueReference();
            response.setValue(value);
        } catch (Exception e) {
            response.setValue(attribute.getValueReference());
        }

        return response;
    }

    public AppointmentServiceAttributeTypeResponse constructAttributeTypeResponse(AppointmentServiceAttributeType attributeType) {
        AppointmentServiceAttributeTypeResponse response = new AppointmentServiceAttributeTypeResponse();
        response.setUuid(attributeType.getUuid());
        response.setName(attributeType.getName());
        response.setDescription(attributeType.getDescription());
        response.setDatatype(attributeType.getDatatypeClassname());
        response.setMinOccurs(attributeType.getMinOccurs());
        response.setMaxOccurs(attributeType.getMaxOccurs());
        response.setRetired(attributeType.getRetired());
        return response;
    }

    public List<AppointmentServiceAttributeTypeResponse> constructAttributeTypeListResponse(List<AppointmentServiceAttributeType> attributeTypes) {
        return attributeTypes.stream()
                .map(this::constructAttributeTypeResponse)
                .collect(Collectors.toList());
    }

    private BillableServiceSummary resolveBillableServiceSummary(String billableServiceUuid) {
        // if (!isBillingModuleStarted()) {
        //     log.warn("Billing module is not started; skipping billableService enrichment for uuid: " + billableServiceUuid);
        //     return null;
        // }
        try {
            IBillableItemsService billableItemsService = Context.getService(IBillableItemsService.class);
            BillableService billableService = billableItemsService.getByUuid(billableServiceUuid);
            if (billableService == null) {
                return null;
            }
            BillableServiceSummary summary = new BillableServiceSummary();
            summary.setUuid(billableService.getUuid());
            summary.setDisplay(billableService.getName());
            summary.setAmount(resolveDefaultPrice(billableService));
            return summary;
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean isBillingModuleStarted() {
        Module billingModule = ModuleFactory.getModuleById("billing");
        return billingModule != null && billingModule.isStarted();
    }

    private BigDecimal resolveDefaultPrice(BillableService bs) {
        if (bs.getServicePrices() == null || bs.getServicePrices().isEmpty()) {
            return null;
        }
        return bs.getServicePrices().get(0).getPrice();
    }
}
