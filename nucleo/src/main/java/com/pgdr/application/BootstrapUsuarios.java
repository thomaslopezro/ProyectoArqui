package com.pgdr.application;

import com.pgdr.domain.Organizacion;
import com.pgdr.domain.Usuario;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

@Singleton @Startup
public class BootstrapUsuarios {
    private static final Logger LOG=Logger.getLogger(BootstrapUsuarios.class.getName());
    @PersistenceContext(unitName="PGDR_PU") private EntityManager em;
    @PostConstruct @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void inicializar(){
      String username=System.getenv("PGDR_BOOTSTRAP_USER");
      String password=System.getenv("PGDR_BOOTSTRAP_PASSWORD");
      if(username==null||username.isBlank()||password==null||password.isBlank()){
        LOG.info("Bootstrap de usuarios desactivado: definir PGDR_BOOTSTRAP_USER y PGDR_BOOTSTRAP_PASSWORD para un primer usuario local.");return;
      }
      username=username.trim().toLowerCase(Locale.ROOT);
      if(!username.matches("[a-z0-9_.-]{3,80}")||password.length()<12||password.length()>256)
        throw new IllegalStateException("Bootstrap invalido: usuario 3-80 caracteres, clave de 12-256 caracteres.");
      List<Usuario> found=em.createQuery("SELECT u FROM Usuario u WHERE u.usuario=:u",Usuario.class)
          .setParameter("u",username).setMaxResults(1).getResultList();
      if(!found.isEmpty()){LOG.info("Usuario bootstrap ya existente; no se modifica.");return;}
      UUID org=UUID.randomUUID();
      em.persist(new Organizacion(org,"Organizacion de laboratorio"));
      String salt=Criptografia.sal();
      em.persist(new Usuario(UUID.randomUUID(),username,Criptografia.hashPassword(password.toCharArray(),salt),salt,"COORDINADOR",org));
      LOG.info("Usuario coordinador inicial creado para laboratorio. Retirar la contraseña de bootstrap del entorno.");
    }
}
