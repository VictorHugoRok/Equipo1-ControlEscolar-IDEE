package com.idee.controlescolar.model;

/**
 * Estados posibles de un certificado electrónico según flujo SEP/MEC.
 * Sigue el mismo ciclo que los títulos electrónicos.
 */
public enum EstatusCertificado {
    GENERADO("Certificado generado, pendiente de firma"),
    FIRMADO("Certificado firmado digitalmente"),
    ENVIADO_SEP("Certificado enviado a la SEP para validación"),
    VALIDADO_SEP("Certificado validado por la SEP"),
    RECHAZADO_SEP("Certificado rechazado por la SEP"),
    ENTREGADO("Certificado entregado al alumno");

    private final String descripcion;

    EstatusCertificado(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
