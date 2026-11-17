package co.edu.javeriana.emergencias.dominio.desastres;

public class Sistema {
    public String organizacionId;
    public String id;
    public String nombre;
    public String correo;

    public void validar() {
        if (id == null || !id.matches("[a-z0-9-]{1,50}"))
            throw new IllegalArgumentException("id del sistema: 1 a 50 letras minusculas, numeros o guiones");
        Desastre.validarTexto(nombre, "nombre", 100);
        if (correo == null || correo.length() > 254 || !correo.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+"))
            throw new IllegalArgumentException("correo invalido; debe ser una direccion individual");
    }

    public String cola() { return "sistema." + id; }
}
