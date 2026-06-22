package org.openmrs.module.appointments.service.impl;

import org.openmrs.Provider;
import org.openmrs.module.appointments.dao.AppointmentDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentSlotAvailability;
import org.openmrs.module.appointments.model.AppointmentStatus;
import org.openmrs.module.appointments.model.ServiceWeeklyAvailability;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.service.AppointmentSlotAvailabilityService;
import org.openmrs.module.appointments.util.AppointmentServiceCapacityUtil;
import java.sql.Time;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AppointmentSlotAvailabilityServiceImpl implements AppointmentSlotAvailabilityService {
    private AppointmentServiceDefinitionService appointmentServiceDefinitionService;
    private AppointmentDao appointmentDao;

    public void setAppointmentServiceDefinitionService(
            AppointmentServiceDefinitionService appointmentServiceDefinitionService) {
        this.appointmentServiceDefinitionService = appointmentServiceDefinitionService;
    }

    public void setAppointmentDao(AppointmentDao appointmentDao) {
        this.appointmentDao = appointmentDao;
    }

    @Override
    public List<AppointmentSlotAvailability> getAvailableSlots(String serviceUuid, Date date,
            String excludeAppointmentUuid) {
        AppointmentServiceDefinition service = appointmentServiceDefinitionService
                .getAppointmentServiceByUuid(serviceUuid);
        if (service == null) {
            throw new IllegalArgumentException("Appointment Service does not exist");
        }
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }

        Provider provider = service.getProvider();
        int durationMins = AppointmentServiceCapacityUtil.resolveDurationMins(service, null);
        DayOfWeek dayOfWeek = AppointmentServiceCapacityUtil.toDayOfWeek(date);

        List<AppointmentSlotAvailability> result = new ArrayList<>();
        List<ServiceWeeklyAvailability> weeklyAvailabilities = AppointmentServiceCapacityUtil
                .getWeeklyAvailabilitiesForDay(service, dayOfWeek);
        if (AppointmentServiceCapacityUtil.hasWeeklyAvailability(service)) {
            for (ServiceWeeklyAvailability weeklyAvailability : weeklyAvailabilities) {
                result.addAll(buildSlotsForWindow(service, provider, date, weeklyAvailability.getStartTime(),
                        weeklyAvailability.getEndTime(), durationMins, weeklyAvailability, excludeAppointmentUuid));
            }
        } else {
            result.addAll(buildSlotsForWindow(service, provider, date, service.getStartTime(), service.getEndTime(),
                    durationMins, null, excludeAppointmentUuid));
        }
        return result;
    }

    @Override
    public boolean isSlotCapacityExceeded(Appointment appointment) {
        if (appointment == null
                || appointment.getService() == null
                || appointment.getStartDateTime() == null
                || AppointmentStatus.Cancelled.equals(appointment.getStatus())) {
            return false;
        }
        AppointmentServiceDefinition service = appointment.getService();
        DayOfWeek dayOfWeek = AppointmentServiceCapacityUtil.toDayOfWeek(appointment.getStartDateTime());
        ServiceWeeklyAvailability weeklyAvailability = resolveWeeklyAvailabilityForAppointment(service, dayOfWeek,
                appointment.getStartDateTime());
        Integer capacity = AppointmentServiceCapacityUtil.resolveMaxAppointmentsPerSlot(service, weeklyAvailability);
        if (capacity == null) {
            return false;
        }
        Date slotStart = appointment.getStartDateTime();
        Date slotEnd = AppointmentServiceCapacityUtil.resolveAppointmentEndDateTime(appointment);
        Provider provider = service.getProvider();
        int booked = countBookedAppointments(service, provider, slotStart, slotEnd, appointment.getUuid());
        return booked >= capacity;
    }

    private List<AppointmentSlotAvailability> buildSlotsForWindow(AppointmentServiceDefinition service,
            Provider provider,
            Date date,
            Time startTime,
            Time endTime,
            int durationMins,
            ServiceWeeklyAvailability weeklyAvailability,
            String excludeAppointmentUuid) {
        List<AppointmentSlotAvailability> slots = new ArrayList<>();
        Integer capacity = AppointmentServiceCapacityUtil.resolveMaxAppointmentsPerSlot(service, weeklyAvailability);
        List<Date[]> timeSlots = AppointmentServiceCapacityUtil.generateSlots(date, startTime, endTime, durationMins);

        for (Date[] window : timeSlots) {
            AppointmentSlotAvailability slot = new AppointmentSlotAvailability();
            Date s = window[0];
            Date e = window[1];
            slot.setStartDateTime(s);
            slot.setEndDateTime(e);
            slot.setCapacity(capacity);

            if (capacity == null) {
                slot.setBooked(null);
                slot.setAvailable(null);
            } else {
                int booked = countBookedAppointments(service, provider, s, e, excludeAppointmentUuid);
                slot.setBooked(booked);
                slot.setAvailable(Math.max(capacity - booked, 0));
            }
            slots.add(slot);
        }

        return slots;
    }

    private int countBookedAppointments(AppointmentServiceDefinition service, Provider provider, Date slotStart, Date slotEnd, String excludeAppointmentUuid) {
        List<AppointmentStatus> statuses = AppointmentServiceCapacityUtil.OCCUPYING_STATUSES;
        if (provider != null) {
            return appointmentDao.countOverlappingAppointmentsForProvider(provider, slotStart, slotEnd, excludeAppointmentUuid, statuses);
        }
        return appointmentDao.countOverlappingAppointmentsForService(service, provider, slotStart, slotEnd, excludeAppointmentUuid, statuses);
    }

    private ServiceWeeklyAvailability resolveWeeklyAvailabilityForAppointment(AppointmentServiceDefinition service,
            DayOfWeek dayOfWeek,
            Date appointmentStart) {
        List<ServiceWeeklyAvailability> availabilities = AppointmentServiceCapacityUtil
                .getWeeklyAvailabilitiesForDay(service, dayOfWeek);

        for (ServiceWeeklyAvailability availability : availabilities) {
            List<Date[]> slots = AppointmentServiceCapacityUtil.generateSlots(
                    appointmentStart,
                    availability.getStartTime(),
                    availability.getEndTime(),
                    AppointmentServiceCapacityUtil.resolveDurationMins(service, null));

            for (Date[] slot : slots) {
                if (!appointmentStart.before(slot[0]) && appointmentStart.before(slot[1])) {
                    return availability;
                }
            }
        }
        return availabilities.isEmpty() ? null : availabilities.get(0);
    }
}
