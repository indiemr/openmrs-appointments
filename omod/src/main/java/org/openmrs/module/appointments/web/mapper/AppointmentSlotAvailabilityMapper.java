package org.openmrs.module.appointments.web.mapper;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

import org.openmrs.module.appointments.model.AppointmentSlotAvailability;
import org.openmrs.module.appointments.web.contract.AppointmentSlotAvailabilityResponse;
import org.springframework.stereotype.Component;

@Component
public class AppointmentSlotAvailabilityMapper {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    public List<AppointmentSlotAvailabilityResponse> constructResponse(List<AppointmentSlotAvailability> slots) {
        return slots.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private AppointmentSlotAvailabilityResponse toResponse(AppointmentSlotAvailability slot) {
        AppointmentSlotAvailabilityResponse response = new AppointmentSlotAvailabilityResponse();
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_PATTERN);
        response.setStartDateTime(formatter.format(slot.getStartDateTime()));
        response.setEndDateTime(formatter.format(slot.getEndDateTime()));
        response.setCapacity(slot.getCapacity());
        response.setBooked(slot.getBooked());
        response.setAvailable(slot.getAvailable());
        return response;
    }
}
