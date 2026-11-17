package com.pgdr.infrastructure;

import com.pgdr.domain.AuditoriaDesastre;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;

@Stateless
public class AuditoriaRepository {
    @PersistenceContext(unitName="PGDR_PU") private EntityManager em;
    public void guardar(AuditoriaDesastre a){em.persist(a);em.flush();}
}
