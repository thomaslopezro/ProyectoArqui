package com.pgdr.infrastructure;

import com.pgdr.domain.Afectacion;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Stateless
public class AfectacionRepository {
    @PersistenceContext(unitName="PGDR_PU") private EntityManager em;
    public boolean insertarSiNoExiste(UUID id,UUID org,UUID solicitud,UUID desastre,BigDecimal lat,BigDecimal lon,int nivel,String nota,String huella){
      return em.createNativeQuery("INSERT INTO afectaciones (id,organizacion_id,solicitud_id,desastre_id,latitud,longitud,nivel_afectacion,observacion,huella_solicitud,creado_en) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10) ON CONFLICT (organizacion_id,solicitud_id) DO NOTHING")
      .setParameter(1,id).setParameter(2,org).setParameter(3,solicitud).setParameter(4,desastre).setParameter(5,lat).setParameter(6,lon)
      .setParameter(7,nivel).setParameter(8,nota).setParameter(9,huella).setParameter(10,Timestamp.from(Instant.now())).executeUpdate()==1;
    }
    public Afectacion buscar(UUID id){return em.find(Afectacion.class,id);}
    public Afectacion porSolicitud(UUID org,UUID solicitud){
      List<Afectacion> list=em.createQuery("SELECT a FROM Afectacion a WHERE a.organizacionId=:org AND a.solicitudId=:sol",Afectacion.class)
       .setParameter("org",org).setParameter("sol",solicitud).setMaxResults(1).getResultList();
      return list.isEmpty()?null:list.get(0);
    }
    public List<Afectacion> listar(UUID org,UUID desastre,int limite){
      return em.createQuery("SELECT a FROM Afectacion a WHERE a.organizacionId=:org AND a.desastreId=:desastre ORDER BY a.creadoEn DESC",Afectacion.class)
       .setParameter("org",org).setParameter("desastre",desastre).setMaxResults(limite).getResultList();
    }
}
