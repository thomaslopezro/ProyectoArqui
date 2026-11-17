package com.pgdr.application;

import com.pgdr.domain.Usuario;
import com.pgdr.domain.Sesion;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Stateless
public class AuthService {
    @PersistenceContext(unitName="PGDR_PU") private EntityManager em;
    public record LoginExitoso(String token,Instant expiraEn,Identidad identidad) {}

    public LoginExitoso login(String username,String clave){
      if(username==null||username.isBlank()||username.length()>80||clave==null||clave.isEmpty()||clave.length()>256){return null;}
      List<Usuario> found=em.createQuery("SELECT u FROM Usuario u WHERE u.usuario=:username",Usuario.class)
          .setParameter("username",username.trim().toLowerCase(java.util.Locale.ROOT)).setMaxResults(1).getResultList();
      if(found.isEmpty()){return null;}
      Usuario u=found.get(0);
      if(!u.isActivo()||!Criptografia.comprobar(clave.toCharArray(),u.getPasswordSalt(),u.getPasswordHash())) return null;
      String token=Criptografia.token();
      Instant exp=Instant.now().plus(8,ChronoUnit.HOURS);
      em.persist(new Sesion(Criptografia.sha256(token),u.getId(),exp));
      return new LoginExitoso(token,exp,new Identidad(u.getId(),u.getOrganizacionId(),u.getUsuario(),u.getRol()));
    }
    public Identidad resolver(String token){
      if(token==null||!token.matches("[A-Za-z0-9_-]{43}")) return null;
      Sesion ses=em.find(Sesion.class,Criptografia.sha256(token));
      if(ses==null||!ses.getExpiraEn().isAfter(Instant.now())) return null;
      Usuario u=em.find(Usuario.class,ses.getUsuarioId());
      if(u==null||!u.isActivo()) return null;
      return new Identidad(u.getId(),u.getOrganizacionId(),u.getUsuario(),u.getRol());
    }
    public boolean crearUsuario(Identidad actor,String username,String password,String rol){
      if(!actor.puedeRegistrarDesastre())throw new SecurityException("SIN_PERMISO");
      if(username==null||password==null||rol==null)throw new ValidacionException("Datos incompletos");
      String limpio=username.trim().toLowerCase(java.util.Locale.ROOT);
      if(!limpio.matches("[a-z0-9_.-]{3,80}")||password.length()<12||password.length()>256||!("EVALUADOR".equals(rol)||"CONSULTA".equals(rol)))
        throw new ValidacionException("Usuario, clave o rol invalido");
      if(!em.createQuery("SELECT u FROM Usuario u WHERE u.usuario=:u",Usuario.class)
          .setParameter("u",limpio).setMaxResults(1).getResultList().isEmpty())return false;
      String salt=Criptografia.sal();
      em.persist(new Usuario(java.util.UUID.randomUUID(),limpio,Criptografia.hashPassword(password.toCharArray(),salt),salt,rol,actor.organizacionId()));
      em.flush();
      return true;
    }
    public void logout(String token){
      if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))return;
      Sesion s=em.find(Sesion.class,Criptografia.sha256(token));
      if(s!=null)em.remove(s);
    }
}
