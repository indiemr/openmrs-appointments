package org.openmrs.module.appointments.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.dao.AppointmentDao;
import org.openmrs.module.appointments.dao.AppointmentHoldDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentHoldStatus;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.ServiceWeeklyAvailability;
import org.openmrs.module.appointments.service.AppointmentHoldService;
import org.openmrs.module.appointments.util.AppointmentServiceCapacityUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Transactional
public class AppointmentHoldServiceImpl implements AppointmentHoldService {

    public static final String GP_HOLD_MINUTES = "appointments.holdMinutes";
    public static final int DEFAULT_HOLD_MINUTES = 7;
    public static final String GP_HOLD_EXTEND_MINUTES = "appointments.holdExtendMinutes";
    public static final int DEFAULT_HOLD_EXTEND_MINUTES = 7;
    public static final String GP_HOLD_MAX_MINUTES = "appointments.holdMaxMinutes";
    public static final int DEFAULT_HOLD_MAX_MINUTES = 30;
    private static final int LOCK_TIMEOUT_SECONDS = 5;

    private final Log log = LogFactory.getLog(getClass());
    private AppointmentHoldDao appointmentHoldDao;
    private AppointmentDao appointmentDao;

    public void setAppointmentHoldDao(AppointmentHoldDao appointmentHoldDao) {
        this.appointmentHoldDao = appointmentHoldDao;
    }

    public void setAppointmentDao(AppointmentDao appointmentDao) {
        this.appointmentDao = appointmentDao;
    }

    @Override
    public AppointmentHold createHold(AppointmentHold hold) {
        validateForCreate(hold);
        if (hold.getEndDateTime() == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(hold.getStartDateTime());
            calendar.add(Calendar.MINUTE, AppointmentServiceCapacityUtil.resolveDurationMins(
                    hold.getService(), null));
            hold.setEndDateTime(calendar.getTime());
        }

        String lockKey = slotLockKey(hold.getService(), hold.getStartDateTime());
        if (!appointmentHoldDao.acquireSlotLock(lockKey, LOCK_TIMEOUT_SECONDS)) {
            throw new APIException("Could not lock slot. Please retry.");
        }
        try {
            int capacity = resolveCapacity(hold);
            Provider provider = hold.getService().getProvider();
            int bookedAppointments;
            if (provider != null) {
                bookedAppointments = appointmentDao.countOverlappingAppointmentsForProvider(
                        provider,
                        hold.getStartDateTime(),
                        hold.getEndDateTime(),
                        null,
                        AppointmentServiceCapacityUtil.SLOT_BLOCKING_STATUSES);
            } else {
                bookedAppointments = appointmentDao.countOverlappingAppointmentsForService(
                        hold.getService(),
                        provider,
                        hold.getStartDateTime(),
                        hold.getEndDateTime(),
                        null,
                        AppointmentServiceCapacityUtil.SLOT_BLOCKING_STATUSES);
            }
            int bookedHolds = appointmentHoldDao.countUnexpiredOverlapping(
                hold.getService(), hold.getStartDateTime(), hold.getEndDateTime(), null);
            if (bookedAppointments + bookedHolds >= capacity) {
                throw new APIException("Selected time slot is full. Please choose another slot.");
            }

            hold.setStatus(AppointmentHoldStatus.HELD);
            hold.setExpiresAt(new Date(System.currentTimeMillis() + holdMinutes() * 60_000L));
            return appointmentHoldDao.save(hold);
        } finally {
            appointmentHoldDao.releaseSlotLock(lockKey);
        }
    }

    @Override
    public AppointmentHold consumeHold(String holdUuid, Appointment appointment) {
        boolean consumed = appointmentHoldDao.consumeIfActive(holdUuid, appointment);
        if (!consumed) {
            appointmentHoldDao.expireIfDue(holdUuid);
            throw new APIException("Appointment hold has expired or is no longer active.");
        }
        AppointmentHold hold = appointmentHoldDao.getByUuid(holdUuid);
        log.info("Consumed appointment hold " + holdUuid);
        return hold;
    }

    @Override
    public AppointmentHold releaseHold(String holdUuid) {
        if (StringUtils.isBlank(holdUuid)) {
            throw new APIException("holdUuid is required");
        }
        // Update before load so the session doesn't hand back a stale HELD entity
        boolean released = appointmentHoldDao.releaseIfHeld(holdUuid, Context.getAuthenticatedUser(), new Date());
        AppointmentHold hold = appointmentHoldDao.getByUuid(holdUuid);
        if (hold == null) {
            return null;
        }
        if (!released && AppointmentHoldStatus.CONSUMED.equals(hold.getStatus())) {
            throw new APIException("Appointment hold has already been used to book an appointment. Cancel the appointment instead.");
        }
        if (released) {
            log.info("Released appointment hold " + holdUuid);
        }
        return hold;
    }

    @Override
    public AppointmentHold extendHold(String holdUuid) {
        if (StringUtils.isBlank(holdUuid)) {
            throw new APIException("holdUuid is required");
        }
        AppointmentHold hold = appointmentHoldDao.getByUuid(holdUuid);
        if (hold == null) {
            return null;
        }
        Date now = new Date();
        if (!AppointmentHoldStatus.HELD.equals(hold.getStatus()) || !hold.getExpiresAt().after(now)) {
            throw new APIException("Appointment hold has expired or is no longer active. Please hold the slot again.");
        }

        // New expiry is now + extend window, never beyond dateCreated + max hold time
        Date maxExpiresAt = new Date(hold.getDateCreated().getTime() + holdMaxMinutes() * 60_000L);
        if (!hold.getExpiresAt().before(maxExpiresAt)) {
            throw new APIException("Maximum hold time of " + holdMaxMinutes() + " minutes reached for this slot.");
        }
        Date requested = new Date(now.getTime() + holdExtendMinutes() * 60_000L);
        Date newExpiresAt = requested.before(maxExpiresAt) ? requested : maxExpiresAt;
        if (!newExpiresAt.after(hold.getExpiresAt())) {
            // Already extended past this point (e.g. repeated call); nothing to do
            return hold;
        }

        if (!appointmentHoldDao.extendIfActive(hold, newExpiresAt, Context.getAuthenticatedUser(), now)) {
            throw new APIException("Appointment hold has expired or is no longer active. Please hold the slot again.");
        }
        log.info("Extended appointment hold " + holdUuid + " until " + newExpiresAt);
        return hold;
    }

    @Override
    public boolean expireHold(String holdUuid) {
        return appointmentHoldDao.expireIfDue(holdUuid);
    }

    @Override
    public int expireDueHolds() {
        int expired = appointmentHoldDao.expireAllDue();
        log.info("Expired " + expired + " appointment hold(s)");
        return expired;
    }

    @Override
    public AppointmentHold getByUuid(String uuid) {
        return appointmentHoldDao.getByUuid(uuid);
    }

    private void validateForCreate(AppointmentHold hold) {
        if (hold == null || hold.getService() == null || hold.getPatient() == null
                || hold.getStartDateTime() == null) {
            throw new APIException("service, patient and startDateTime are required");
        }
    }

    private int holdMinutes() {
        return minutesProperty(GP_HOLD_MINUTES, DEFAULT_HOLD_MINUTES);
    }

    private int holdExtendMinutes() {
        return minutesProperty(GP_HOLD_EXTEND_MINUTES, DEFAULT_HOLD_EXTEND_MINUTES);
    }

    private int holdMaxMinutes() {
        return minutesProperty(GP_HOLD_MAX_MINUTES, DEFAULT_HOLD_MAX_MINUTES);
    }

    private int minutesProperty(String property, int defaultMinutes) {
        String value = Context.getAdministrationService()
                .getGlobalProperty(property, String.valueOf(defaultMinutes));
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultMinutes;
        } catch (Exception e) {
            return defaultMinutes;
        }
    }

    private int resolveCapacity(AppointmentHold hold) {
        AppointmentServiceDefinition service = hold.getService();
        DayOfWeek dayOfWeek = AppointmentServiceCapacityUtil.toDayOfWeek(hold.getStartDateTime());
        List<ServiceWeeklyAvailability> weekly = AppointmentServiceCapacityUtil
                .getWeeklyAvailabilitiesForDay(service, dayOfWeek);
        ServiceWeeklyAvailability match = weekly.isEmpty() ? null : weekly.get(0);
        int durationMins = AppointmentServiceCapacityUtil.resolveDurationMins(service, null);
        return AppointmentServiceCapacityUtil.resolveSlotCapacity(service, match, durationMins);
    }

    private String slotLockKey(AppointmentServiceDefinition service, Date start) {
        return "ah-" + service.getAppointmentServiceId() + "-" + start.getTime();
    }
}