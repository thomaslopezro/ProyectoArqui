package com.pgdr.infrastructure;

import com.pgdr.domain.Desastre;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Stateless
public class DesastreRepository {
    @PersistenceContext(unitName="PGDR_PU") private EntityManager em;
    public boolean insertarSiNoExiste(UUID id,UUID org,UUID solicitud,String nombre,String descripcion,String severidad,String huella){
      int n=em.createNativeQuery("INSERT INTO desastres (id,nombre,descripcion,severidad,creado_en,version,organizacion_id,solicitud_id,huella_solicitud) VALUES (?1,?2,?3,?4,?5,0,?6,?7,?8) ON CONFLICT (organizacion_id,solicitud_id) DO NOTHING")
      .setParameter(1,id).setParameter(2,nombre).setParameter(3,descripcion).setParameter(4,severidad)
      .setParameter(5,java.sql.Timestamp.from(Instant.now())).setParameter(6,org).setParameter(7,solicitud).setParameter(8,huella).executeUpdate();
      return n==1;
    }
    public Desastre buscar(UUID id,UUID org){
      List<Desastre> list=em.createQuery("SELECT d FROM Desastre d WHERE d.id=:id AND d.organizacionId=:org",Desastre.class)
       .setParameter("id",id).setParameter("org",org).setMaxResults(1).getResultList();
      return list.isEmpty()?null:list.get(0);
    }
    public Desastre porSolicitud(UUID org,UUID solicitud){
      List<Desastre> list=em.createQuery("SELECT d FROM Desastre d WHERE d.organizacionId=:org AND d.solicitudId=:solicitud",Desastre.class)
       .setParameter("org",org).setParameter("solicitud",solicitud).setMaxResults(1).getResultList();
      return list.isEmpty()?null:list.get(0);
    }
    public List<Desastre> listar(UUID org,int max){
      return em.createQuery("SELECT d FROM Desastre d WHERE d.organizacionId=:org ORDER BY d.creadoEn DESC",Desastre.class)
       .setParameter("org",org).setMaxResults(max).getResultList();
    }
}
