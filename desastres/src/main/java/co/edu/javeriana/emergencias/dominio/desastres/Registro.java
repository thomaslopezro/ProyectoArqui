package co.edu.javeriana.emergencias.dominio.desastres;

import java.util.ArrayList;
import java.util.List;

public class Registro {
    public String organizacionId;
    public Desastre desastre;
    public String registradoEn;
    public List<Sistema> destinatarios = new ArrayList<>();
    public boolean notificacionPublicada;
    public List<String> correosEnviados = new ArrayList<>();
}
