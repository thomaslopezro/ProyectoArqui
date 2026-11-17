package com.pgdr.api;

import java.math.BigDecimal;
import java.util.UUID;
public class AfectacionRequest {
    public UUID solicitudId;
    public UUID desastreId;
    public BigDecimal latitud;
    public BigDecimal longitud;
    public Integer nivelAfectacion;
    public String observacion;
}
