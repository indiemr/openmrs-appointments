package org.openmrs.module.appointments.events.advice;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.events.AppointmentRescheduledEvent;
import org.openmrs.module.appointments.events.publisher.AppointmentEventPublisher;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.ThrowsAdvice;

import java.lang.reflect.Method;
import java.util.Set;

public class RescheduleEventsAdvice implements AfterReturningAdvice, MethodBeforeAdvice, ThrowsAdvice {

    private static final String RESCHEDULE_METHOD = "reschedule";

    private final Logger log = LogManager.getLogger(RescheduleEventsAdvice.class);
    private final AppointmentEventPublisher eventPublisher;
    private final Set<String> adviceMethodNames = Sets.newHashSet(RESCHEDULE_METHOD);

    public RescheduleEventsAdvice() {
        this.eventPublisher = Context.getRegisteredComponent("appointmentEventPublisher", AppointmentEventPublisher.class);
    }

    @Override
    public void before(Method method, Object[] arguments, Object target) {
        if (!adviceMethodNames.contains(method.getName())) {
            return;
        }
        String originalAppointmentUuid = (String) arguments[0];
        Appointment previousAppointment = Context.getService(AppointmentsService.class)
                .getAppointmentByUuid(originalAppointmentUuid);
        if (previousAppointment != null) {
            RescheduleEventContext.begin(previousAppointment);
        }
    }

    @Override
    public void afterReturning(Object returnValue, Method method, Object[] arguments, Object target) {
        if (!adviceMethodNames.contains(method.getName())) {
            return;
        }
        try {
            Appointment previousAppointment = RescheduleEventContext.getPreviousAppointment();
            if (previousAppointment == null || returnValue == null) {
                return;
            }
            Appointment rescheduledAppointment = (Appointment) returnValue;
            AppointmentRescheduledEvent event = new AppointmentRescheduledEvent(previousAppointment, rescheduledAppointment);
            eventPublisher.publishEvent(event);
            log.info("Successfully published reschedule event with uuid : " + event.payloadId);
        } finally {
            RescheduleEventContext.clear();
        }
    }

    public void afterThrowing(Method method, Object[] arguments, Object target, Throwable ex) {
        if (adviceMethodNames.contains(method.getName())) {
            RescheduleEventContext.clear();
        }
    }
}
