package com.idee.controlescolar.model;

public enum EstatusTitulo {
    GENERADO("Título generado, pendiente de firma"),
    FIRMADO("Título firmado digitalmente"),
    ENVIADO_SEP("Título enviado a la SEP para validación"),
    VALIDADO_SEP("Título validado por la SEP"),
    RECHAZADO_SEP("Título rechazado por la SEP"),
    ENTREGADO("Título entregado al alumno");

    private final String descripcion;

    EstatusTitulo(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
