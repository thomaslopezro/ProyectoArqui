package com.pgdr.application;

import java.math.BigDecimal;
public final class Huellas {
    private Huellas(){}
    private static String campo(String s){return s.length()+":"+s;}
    public static String desastre(String nombre,String descripcion,int nivel){
      return Criptografia.sha256(campo(nombre.trim())+campo(descripcion==null?"":descripcion)+nivel);
    }
    public static String afectacion(String desastreId,BigDecimal lat,BigDecimal lon,int nivel,String observacion){
      return Criptografia.sha256(desastreId+"\n"+lat.stripTrailingZeros().toPlainString()+"\n"+lon.stripTrailingZeros().toPlainString()+"\n"+nivel+"\n"+(observacion==null?"":observacion));
    }
}
