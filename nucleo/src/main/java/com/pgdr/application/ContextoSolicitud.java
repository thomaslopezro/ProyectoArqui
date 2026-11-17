package com.pgdr.application;
import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;
@RequestScoped
public class ContextoSolicitud {
 private Identidad actor;
 private UUID desastreId,solicitudId;
 private String huella;
 private boolean creada;
 private UUID evaluacionId; private Long evaluacionVersion;private String evaluacionResumen;
 public UUID evaluacionId(){return evaluacionId;}public void evaluacionId(UUID v){evaluacionId=v;}
 public Long evaluacionVersion(){return evaluacionVersion;}public void evaluacionVersion(Long v){evaluacionVersion=v;}
 public String evaluacionResumen(){return evaluacionResumen;}public void evaluacionResumen(String v){evaluacionResumen=v;}
 public void actor(Identidad valor){actor=valor;}
 public UUID desastreId(){return desastreId;} public void desastreId(UUID valor){desastreId=valor;}
 public UUID solicitudId(){return solicitudId;} public void solicitudId(UUID valor){solicitudId=valor;}
 public String huella(){return huella;} public void huella(String valor){huella=valor;}
 public boolean creada(){return creada;} public void creada(boolean valor){creada=valor;}
 public Identidad identidad(){if(actor==null)throw new SecurityException("SIN_IDENTIDAD");return actor;}
}
