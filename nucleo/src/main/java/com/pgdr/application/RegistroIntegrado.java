package com.pgdr.application;

import jakarta.ejb.*;
import jakarta.persistence.*;
import jakarta.json.*;
import java.io.StringReader;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Stateless
public class RegistroIntegrado {
 @PersistenceContext(unitName="PGDR_PU") private EntityManager em;
 @EJB private DesastreService service;
 public JsonObject leer(String s){return Json.createReader(new StringReader(s)).readObject();}
 public JsonObject normalizar(JsonObject d){
  String id=d.getString("id","");
  if(!id.matches("[A-Za-z0-9_-]{1,100}"))throw new ValidacionException("Identificador invalido");
  for(String k:List.of("tipo","ubicacion","descripcion","severidad","fecha")){
   if(!(d.get(k) instanceof JsonString) || d.getString(k).isBlank())throw new ValidacionException("Falta "+k);
  }
  if(d.getString("tipo").length()>80 || d.getString("ubicacion").length()>200 || d.getString("descripcion").length()>2000)
   throw new ValidacionException("Datos demasiado largos");
  if(!List.of("BAJA","MEDIA","ALTA","CRITICA").contains(d.getString("severidad")))throw new ValidacionException("Severidad invalida");
  try{Instant.parse(d.getString("fecha"));}catch(Exception e){throw new ValidacionException("Fecha ISO UTC invalida");}
  return Json.createObjectBuilder().add("id",id).add("tipo",d.getString("tipo")).add("ubicacion",d.getString("ubicacion"))
   .add("descripcion",d.getString("descripcion")).add("severidad",d.getString("severidad")).add("fecha",d.getString("fecha")).build();
 }
 public record Resultado(JsonObject registro,boolean creado,boolean conflicto){}
 public Resultado registrar(Identidad actor,JsonObject raw){
  if(!actor.puedeRegistrarDesastre())throw new ValidacionException("SIN_PERMISO");
  JsonObject d=normalizar(raw);String ref=d.getString("id");
  UUID sol=UUID.nameUUIDFromBytes((actor.organizacionId()+":"+ref).getBytes(StandardCharsets.UTF_8));
  int nivel=List.of("BAJA","MEDIA","ALTA","CRITICA").indexOf(d.getString("severidad"))+1;
  var result=service.registrar(actor,sol,d.getString("tipo"),d.toString(),nivel);
  if(result.conflicto())return new Resultado(d,false,true);
  // The unique reference is shared with maps, Android and the original notification workshop.
  List<?> existing=em.createNativeQuery("SELECT desastre_id FROM desastres_integrados WHERE referencia=?1").setParameter(1,ref).getResultList();
  if(!existing.isEmpty() && !existing.get(0).toString().equals(result.desastre().getId().toString()))
   throw new ReferenciaOcupada();
  em.createNativeQuery("INSERT INTO desastres_integrados(desastre_id,referencia,datos_json) VALUES (?1,?2,?3) ON CONFLICT (desastre_id) DO NOTHING")
   .setParameter(1,result.desastre().getId()).setParameter(2,ref).setParameter(3,d.toString()).executeUpdate();
  if(result.creado())em.createNativeQuery("UPDATE outbox_eventos SET payload_json=?1 WHERE agregado_id=?2 AND tipo='DESASTRE_REGISTRADO'")
   .setParameter(1,Json.createObjectBuilder(d).add("version",0).build().toString()).setParameter(2,result.desastre().getId()).executeUpdate();
  return new Resultado(d,result.creado(),false);
 }
 public JsonObject datos(UUID id){
  List<?> rows=em.createNativeQuery("SELECT datos_json FROM desastres_integrados WHERE desastre_id=?1").setParameter(1,id).getResultList();
  if(!rows.isEmpty()){
   JsonObject guardado=leer(rows.get(0).toString());
   return Json.createObjectBuilder(guardado).add("version",((Number)em.createNativeQuery("SELECT version FROM desastres WHERE id=?1").setParameter(1,id).getSingleResult()).longValue())
    .add("fase",guardado.getString("fase","REGISTRO")).build();
  }
  var d=em.find(com.pgdr.domain.Desastre.class,id);
  return Json.createObjectBuilder().add("id",id.toString()).add("tipo","OTRO").add("ubicacion","Sin ubicación registrada")
   .add("descripcion",d.getDescripcion()==null?d.getNombre():d.getDescripcion()).add("fecha",d.getCreadoEn().toString())
   .add("version",d.getVersion()).add("fase","REGISTRO").add("severidad",List.of("BAJA","MEDIA","ALTA","CRITICA").get(Integer.parseInt(d.getSeveridad().substring(6))-1)).build();
 }
 public JsonArray catalogo(){
  var b=Json.createArrayBuilder();
  for(var d:em.createQuery("SELECT d FROM Desastre d WHERE d.organizacionId IS NOT NULL ORDER BY d.creadoEn",com.pgdr.domain.Desastre.class).getResultList())
   b.add(Json.createObjectBuilder().add("desastre",datos(d.getId())).add("organizacionId",d.getOrganizacionId().toString())
    .add("registradoEn",d.getCreadoEn().toString()).add("notificacionPublicada",false).add("destinatarios",Json.createArrayBuilder()).add("correosEnviados",Json.createArrayBuilder()));
  return b.build();
 }

 public UUID resolver(String ref,Identidad actor){
  var rows=em.createNativeQuery("SELECT d.id FROM desastres d LEFT JOIN desastres_integrados i ON i.desastre_id=d.id WHERE d.organizacion_id=?1 AND (i.referencia=?2 OR (i.referencia IS NULL AND d.id::text=?2))")
   .setParameter(1,actor.organizacionId()).setParameter(2,ref).getResultList();
  if(rows.isEmpty())throw new jakarta.ws.rs.NotFoundException();return UUID.fromString(rows.get(0).toString());
 }
 public JsonObject cambiar(Identidad actor,String ref,UUID cambio,long base,String severidad,String motivo){
  if(!actor.puedeRegistrarDesastre())throw new jakarta.ws.rs.ForbiddenException();
  if(cambio==null || base<0 || motivo==null || motivo.isBlank() || motivo.length()>500 || !List.of("BAJA","MEDIA","ALTA","CRITICA").contains(severidad==null?"":severidad))throw new ValidacionException("Versión, gravedad y motivo válidos requeridos");
  UUID id=resolver(ref,actor);
  em.createNativeQuery("SELECT id FROM desastres WHERE id=?1 FOR UPDATE").setParameter(1,id).getSingleResult();
  String huella=Criptografia.sha256(id+":"+base+":"+severidad+":"+motivo);
  var prev=em.createNativeQuery("SELECT huella,nuevo FROM desastre_cambios WHERE organizacion_id=?1 AND id=?2").setParameter(1,actor.organizacionId()).setParameter(2,cambio).getResultList();
  if(!prev.isEmpty()){Object[] row=(Object[])prev.get(0);if(!huella.equals(row[0].toString()))throw new CambioConflicto();return leer(row[1].toString());}
  JsonObject antes=datos(id);
  if(antes.getJsonNumber("version").longValue()!=base)throw new CambioConflicto();
  if(antes.getString("severidad").equals(severidad))throw new ValidacionException("La gravedad no cambió");
  JsonObject despues=Json.createObjectBuilder(antes).add("severidad",severidad).add("version",base+1).build();
  em.createNativeQuery("UPDATE desastres SET severidad=?1,version=version+1 WHERE id=?2").setParameter(1,"Nivel_"+(List.of("BAJA","MEDIA","ALTA","CRITICA").indexOf(severidad)+1)).setParameter(2,id).executeUpdate();
  em.createNativeQuery("INSERT INTO desastres_integrados(desastre_id,referencia,datos_json) VALUES (?1,?2,?3) ON CONFLICT(desastre_id) DO UPDATE SET datos_json=EXCLUDED.datos_json").setParameter(1,id).setParameter(2,ref).setParameter(3,despues.toString()).executeUpdate();
  em.createNativeQuery("INSERT INTO desastre_cambios(id,organizacion_id,desastre_id,version,anterior,nuevo,actor_id,motivo,huella) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)")
   .setParameter(1,cambio).setParameter(2,actor.organizacionId()).setParameter(3,id).setParameter(4,base+1).setParameter(5,antes.toString()).setParameter(6,despues.toString()).setParameter(7,actor.usuarioId()).setParameter(8,motivo).setParameter(9,huella).executeUpdate();
  em.persist(new com.pgdr.domain.EventoOutbox(actor.organizacionId(),"DESASTRE_GRAVEDAD_CAMBIADA",id,Json.createObjectBuilder(despues).add("clase","DESASTRE_GRAVEDAD_CAMBIADA").add("motivo",motivo).build().toString()));
  em.flush();em.clear();return despues;
 }
 // Fase del desastre: Registro, Caracterización, Reconstrucción y Cierre. El núcleo es su dueño.
 private static final java.util.Map<String,List<String>> FASES=java.util.Map.of(
  "REGISTRO",List.of("CARACTERIZACION","CIERRE"),
  "CARACTERIZACION",List.of("RECONSTRUCCION","CIERRE"),
  "RECONSTRUCCION",List.of("CIERRE"),
  "CIERRE",List.of());
 public JsonObject cambiarFase(Identidad actor,String ref,UUID cambio,long base,String fase,String motivo){
  if(!actor.puedeRegistrarDesastre())throw new jakarta.ws.rs.ForbiddenException();
  if(cambio==null || base<0 || motivo==null || motivo.isBlank() || motivo.length()>500 || fase==null || !FASES.containsKey(fase))throw new ValidacionException("Versión, fase y motivo válidos requeridos");
  UUID id=resolver(ref,actor);
  em.createNativeQuery("SELECT id FROM desastres WHERE id=?1 FOR UPDATE").setParameter(1,id).getSingleResult();
  String huella=Criptografia.sha256("FASE:"+id+":"+base+":"+fase+":"+motivo);
  var prev=em.createNativeQuery("SELECT huella,nuevo FROM desastre_cambios WHERE organizacion_id=?1 AND id=?2").setParameter(1,actor.organizacionId()).setParameter(2,cambio).getResultList();
  if(!prev.isEmpty()){Object[] row=(Object[])prev.get(0);if(!huella.equals(row[0].toString()))throw new CambioConflicto();return leer(row[1].toString());}
  JsonObject antes=datos(id);
  if(antes.getJsonNumber("version").longValue()!=base)throw new CambioConflicto();
  if(!FASES.getOrDefault(antes.getString("fase","REGISTRO"),List.of()).contains(fase))throw new ValidacionException("Transición de fase no permitida");
  JsonObject despues=Json.createObjectBuilder(antes).add("fase",fase).add("version",base+1).build();
  em.createNativeQuery("UPDATE desastres SET version=version+1 WHERE id=?1").setParameter(1,id).executeUpdate();
  em.createNativeQuery("INSERT INTO desastres_integrados(desastre_id,referencia,datos_json) VALUES (?1,?2,?3) ON CONFLICT(desastre_id) DO UPDATE SET datos_json=EXCLUDED.datos_json").setParameter(1,id).setParameter(2,ref).setParameter(3,despues.toString()).executeUpdate();
  em.createNativeQuery("INSERT INTO desastre_cambios(id,organizacion_id,desastre_id,version,anterior,nuevo,actor_id,motivo,huella) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)")
   .setParameter(1,cambio).setParameter(2,actor.organizacionId()).setParameter(3,id).setParameter(4,base+1).setParameter(5,antes.toString()).setParameter(6,despues.toString()).setParameter(7,actor.usuarioId()).setParameter(8,motivo).setParameter(9,huella).executeUpdate();
  em.flush();em.clear();return despues;
 }
 public JsonArray historial(Identidad actor,String ref){
  UUID id=resolver(ref,actor);var b=Json.createArrayBuilder();
  for(Object raw:em.createNativeQuery("SELECT c.id,c.version,c.anterior,c.nuevo,u.usuario,c.motivo,c.ocurrido_en::text FROM desastre_cambios c JOIN usuarios u ON u.id=c.actor_id WHERE c.desastre_id=?1 ORDER BY c.version").setParameter(1,id).getResultList()){
   Object[] x=(Object[])raw;b.add(Json.createObjectBuilder().add("cambioId",x[0].toString()).add("version",((Number)x[1]).longValue()).add("anterior",leer(x[2].toString())).add("nuevo",leer(x[3].toString())).add("actor",x[4].toString()).add("motivo",x[5].toString()).add("fecha",x[6].toString()));
  }return b.build();
 }
 @ApplicationException(rollback=true) public static class CambioConflicto extends RuntimeException{}

 public Identidad migrador(){
  String name=System.getenv("PGDR_BOOTSTRAP_USER");
  var u=em.createQuery("SELECT u FROM Usuario u WHERE u.usuario=:u",com.pgdr.domain.Usuario.class).setParameter("u",name).getSingleResult();
  return new Identidad(u.getId(),u.getOrganizacionId(),u.getUsuario(),u.getRol());
 }
 @ApplicationException(rollback=true)
 public static class ReferenciaOcupada extends RuntimeException{}
}
