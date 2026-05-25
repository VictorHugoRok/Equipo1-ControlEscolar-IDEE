package com.idee.controlescolar.dto;

import com.idee.controlescolar.model.EstatusCertificado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para la respuesta de un certificado electrónico (DEC).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificadoElectronicoResponse {

    private Long id;
    private String folioControl;

    private Long alumnoId;
    private String alumnoMatricula;
    private String alumnoNombreCompleto;
    private String alumnoCurp;

    private Long programaId;
    private String programaClave;
    private String programaNombre;

    private LocalDate fechaExpedicion;
    private String idTipoCertificacion;
    private String tipoCertificacion;
    private String periodo;
    private String cicloEscolar;

    private EstatusCertificado estatus;
    private String estatusDescripcion;
    private String xmlPath;
    private boolean tieneSello;
    private boolean estaCompleto;
    private boolean validoXsd;

    /**
     * Lista de errores específicos de validación XSD cuando validoXsd es false.
     * Indica exactamente qué valor o valores no pasaron la comparación con el XSD.
     */
    private java.util.List<String> erroresXsd;

    private String observaciones;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}
