package com.pgdr.application;
import jakarta.ejb.*;
import jakarta.persistence.*;
import jakarta.json.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

@Singleton @Startup
public class PublicadorOutbox {
 @PersistenceContext(unitName="PGDR_PU") EntityManager em;
 @EJB RegistroIntegrado registro;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 private static final Logger LOG=Logger.getLogger(PublicadorOutbox.class.getName());
 @Schedule(hour="*",minute="*",second="*/1",persistent=false)
 public void publicar(){
  for(Object raw:em.createNativeQuery("SELECT id,agregado_id,organizacion_id,payload_json,tipo FROM outbox_eventos WHERE publicado_en IS NULL AND tipo IN ('DESASTRE_REGISTRADO','DESASTRE_GRAVEDAD_CAMBIADA') ORDER BY creado_en LIMIT 20",Object[].class).getResultList()){
   Object[] row=(Object[])raw;
   try{
    JsonObject snapshot=registro.leer(row[3].toString());
    if(!snapshot.containsKey("tipo")){
     var originales=em.createNativeQuery("SELECT anterior FROM desastre_cambios WHERE desastre_id=?1 ORDER BY version LIMIT 1").setParameter(1,row[1]).getResultList();
     snapshot=originales.isEmpty()?registro.datos(UUID.fromString(row[1].toString())):registro.leer(originales.get(0).toString());
    }
    String ref=snapshot.getString("id");
    JsonObject body=Json.createObjectBuilder(snapshot).add("eventoId",ref)
     .add("id",row[4].toString().equals("DESASTRE_REGISTRADO")?ref:row[0].toString())
     .add("organizacionId",row[2].toString()).build();
    var req=HttpRequest.newBuilder(URI.create(System.getenv("NOTIFICACIONES_URL")+"/desastres"))
     .timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    var res=http.send(req,HttpResponse.BodyHandlers.discarding());
    if(res.statusCode()==202 || res.statusCode()==200)
     em.createNativeQuery("UPDATE outbox_eventos SET publicado_en=now() WHERE id=?1").setParameter(1,row[0]).executeUpdate();
    else LOG.warning("Notificación pendiente; HTTP "+res.statusCode());
   }catch(Exception e){LOG.warning("Se reintentará notificación: "+e.getClass().getSimpleName());if(e instanceof InterruptedException){Thread.currentThread().interrupt();return;}}
  }
 }
}
