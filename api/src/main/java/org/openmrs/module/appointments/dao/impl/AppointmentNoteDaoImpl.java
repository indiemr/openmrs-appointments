package org.openmrs.module.appointments.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openmrs.module.appointments.dao.AppointmentNoteDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentNote;
import org.openmrs.module.appointments.model.AppointmentNoteType;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AppointmentNoteDaoImpl implements AppointmentNoteDao {

    private static final int IN_CLAUSE_CHUNK_SIZE = 500;

    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    @Override
    public AppointmentNote save(AppointmentNote note) {
        sessionFactory.getCurrentSession().saveOrUpdate(note);
        return note;
    }

    @Override
    public AppointmentNote getByUuid(String uuid) {
        Criteria criteria = sessionFactory.getCurrentSession()
                .createCriteria(AppointmentNote.class);
        criteria.add(Restrictions.eq("uuid", uuid));
        return (AppointmentNote) criteria.uniqueResult();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AppointmentNote> getNotes(Appointment appointment, AppointmentNoteType noteType, boolean includeVoided) {
        Criteria criteria = sessionFactory.getCurrentSession()
                .createCriteria(AppointmentNote.class);
        criteria.add(Restrictions.eq("appointment", appointment));
        if (noteType != null) {
            criteria.add(Restrictions.eq("noteType", noteType));
        }
        if (!includeVoided) {
            criteria.add(Restrictions.eq("voided", false));
        }
        criteria.addOrder(Order.asc("dateCreated"));
        criteria.addOrder(Order.asc("appointmentNoteId"));
        return criteria.list();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AppointmentNote> getNotes(Collection<Appointment> appointments, boolean includeVoided) {
        List<AppointmentNote> notes = new ArrayList<>();
        if (appointments == null || appointments.isEmpty()) {
            return notes;
        }
        List<Appointment> all = new ArrayList<>(appointments);
        // Chunked to stay under database IN-list limits on large list/search pages
        for (int from = 0; from < all.size(); from += IN_CLAUSE_CHUNK_SIZE) {
            List<Appointment> chunk = all.subList(from, Math.min(from + IN_CLAUSE_CHUNK_SIZE, all.size()));
            Criteria criteria = sessionFactory.getCurrentSession()
                    .createCriteria(AppointmentNote.class);
            criteria.add(Restrictions.in("appointment", chunk));
            if (!includeVoided) {
                criteria.add(Restrictions.eq("voided", false));
            }
            criteria.addOrder(Order.asc("dateCreated"));
            criteria.addOrder(Order.asc("appointmentNoteId"));
            notes.addAll(criteria.list());
        }
        return notes;
    }
}
