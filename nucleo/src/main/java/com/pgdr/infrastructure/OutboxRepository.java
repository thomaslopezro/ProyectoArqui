package com.pgdr.infrastructure;

import com.pgdr.domain.EventoOutbox;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;

@Stateless
public class OutboxRepository {
    @PersistenceContext(unitName="PGDR_PU") private EntityManager em;
    public void guardar(EventoOutbox e){em.persist(e);em.flush();}
}
