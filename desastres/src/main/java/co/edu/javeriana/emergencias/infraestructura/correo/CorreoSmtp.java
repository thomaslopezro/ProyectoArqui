package co.edu.javeriana.emergencias.infraestructura.correo;

import co.edu.javeriana.emergencias.aplicacion.puerto.NotificadorCorreo;
import co.edu.javeriana.emergencias.dominio.desastres.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;

@ApplicationScoped
public class CorreoSmtp implements NotificadorCorreo {
    @Override public void enviar(Desastre desastre, Sistema destinatario) {
        try {
            Properties p = new Properties();
            p.setProperty("mail.smtp.host", System.getenv().getOrDefault("SMTP_HOST", "mailpit"));
            p.setProperty("mail.smtp.port", System.getenv().getOrDefault("SMTP_PORT", "1025"));
            p.setProperty("mail.smtp.connectiontimeout", "4000");
            p.setProperty("mail.smtp.timeout", "4000");
            p.setProperty("mail.smtp.writetimeout", "4000");
            MimeMessage mensaje = new MimeMessage(Session.getInstance(p));
            mensaje.setFrom(new InternetAddress("alertas@emergencias.local"));
            mensaje.setRecipient(Message.RecipientType.TO, new InternetAddress(destinatario.correo, true));
            String ref=desastre.eventoId==null?desastre.id:desastre.eventoId;
            mensaje.setSubject(("DESASTRE_GRAVEDAD_CAMBIADA".equals(desastre.clase)?"Cambio de gravedad: ":"Alerta de desastre: ") + desastre.tipo + " [" + ref + "]", "UTF-8");
            mensaje.setText("Sistema: " + destinatario.nombre + "\nID: " + ref + "\nTipo: " + desastre.tipo
                    + "\nUbicacion: " + desastre.ubicacion + "\nSeveridad: " + desastre.severidad
                    + "\nVersión: " + (desastre.version==null?0:desastre.version) + "\nMotivo: " + (desastre.motivo==null?"Registro inicial":desastre.motivo) + "\nFecha: " + desastre.fecha + "\nDescripcion: " + desastre.descripcion, "UTF-8");
            mensaje.setHeader("X-Desastre-ID", ref);
            mensaje.setHeader("X-Notificacion-ID",desastre.id);
            Transport.send(mensaje);
        } catch (MessagingException e) { throw new IllegalStateException("No se pudo enviar el correo por SMTP", e); }
    }
}
