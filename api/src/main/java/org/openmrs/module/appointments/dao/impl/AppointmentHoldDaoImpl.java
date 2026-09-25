package org.openmrs.module.appointments.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.openmrs.module.appointments.dao.AppointmentHoldDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentHoldStatus;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.lang.StringUtils;

import java.util.Date;

public class AppointmentHoldDaoImpl implements AppointmentHoldDao {

    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    @Override
    public AppointmentHold save(AppointmentHold hold) {
        sessionFactory.getCurrentSession().saveOrUpdate(hold);
        return hold;
    }

    @Override
    public AppointmentHold getByUuid(String uuid) {
        Criteria criteria = sessionFactory.getCurrentSession()
                .createCriteria(AppointmentHold.class);
        criteria.add(Restrictions.eq("uuid", uuid));
        return (AppointmentHold) criteria.uniqueResult();
    }

    @Override
    public int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                                        Date slotStart,
                                        Date slotEnd,
                                        String excludeHoldUuid) {
        String hql = "select count(h) from AppointmentHold h "
                + "where h.voided = false "
                + "and h.service.appointmentServiceId = :serviceId "
                + "and h.status = :held "
                + "and h.expiresAt > current_timestamp() "
                + "and h.startDateTime < :slotEnd "
                + "and h.endDateTime > :slotStart";
        if (StringUtils.isNotBlank(excludeHoldUuid)) {
            hql += " and h.uuid <> :excludeUuid";
        }
        Query query = sessionFactory.getCurrentSession().createQuery(hql);
        query.setParameter("serviceId", service.getAppointmentServiceId());
        query.setParameter("held", AppointmentHoldStatus.HELD);
        query.setParameter("slotEnd", slotEnd);
        query.setParameter("slotStart", slotStart);
        if (StringUtils.isNotBlank(excludeHoldUuid)) {
            query.setParameter("excludeUuid", excludeHoldUuid);
        }
        Number count = (Number) query.uniqueResult();
        return count != null ? count.intValue() : 0;
    }

    @Override
    public boolean consumeIfActive(String holdUuid, Appointment appointment) {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "update AppointmentHold h set h.status = :consumed, h.appointment = :appointment "
                        + "where h.uuid = :uuid and h.status = :held and h.expiresAt > current_timestamp()");
        query.setParameter("consumed", AppointmentHoldStatus.CONSUMED);
        query.setParameter("appointment", appointment);
        query.setParameter("uuid", holdUuid);
        query.setParameter("held", AppointmentHoldStatus.HELD);
        return query.executeUpdate() == 1;
    }

    @Override
    public boolean expireIfDue(String holdUuid) {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "update AppointmentHold h set h.status = :expired "
                        + "where h.uuid = :uuid and h.status = :held and h.expiresAt <= current_timestamp()");
        query.setParameter("expired", AppointmentHoldStatus.EXPIRED);
        query.setParameter("uuid", holdUuid);
        query.setParameter("held", AppointmentHoldStatus.HELD);
        return query.executeUpdate() == 1;
    }

    @Override
    public int expireAllDue() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "update AppointmentHold h set h.status = :expired "
                        + "where h.status = :held and h.expiresAt <= current_timestamp()");
        query.setParameter("expired", AppointmentHoldStatus.EXPIRED);
        query.setParameter("held", AppointmentHoldStatus.HELD);
        return query.executeUpdate();
    }

    @Override
    public boolean acquireSlotLock(String lockKey, int timeoutSeconds) {
        Number result = (Number) sessionFactory.getCurrentSession()
                .createSQLQuery("SELECT GET_LOCK(:lockKey, :timeout)")
                .setParameter("lockKey", lockKey)
                .setParameter("timeout", timeoutSeconds)
                .uniqueResult();
        return result != null && result.intValue() == 1;
    }

    @Override
    public void releaseSlotLock(String lockKey) {
        sessionFactory.getCurrentSession()
                .createSQLQuery("SELECT RELEASE_LOCK(:lockKey)")
                .setParameter("lockKey", lockKey)
                .uniqueResult();
    }
}