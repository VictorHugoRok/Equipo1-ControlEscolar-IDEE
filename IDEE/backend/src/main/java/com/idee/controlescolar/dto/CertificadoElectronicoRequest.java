package com.idee.controlescolar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para la solicitud de generación de un certificado electrónico (DEC).
 * idTipoCertificacion: 79 = Total, 80 = Parcial (catálogo SEP).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificadoElectronicoRequest {

    @NotNull(message = "El ID del alumno es requerido")
    private Long alumnoId;

    @NotNull(message = "El ID del programa es requerido")
    private Long programaId;

    /**
     * ID del plantel que emitirá el certificado. Se usa idPlantel del plantel como idEntidad e idNombreInstitucion en el XML.
     */
    @NotNull(message = "El plantel emisor es requerido")
    private Long plantelId;

    @NotNull(message = "La fecha de expedición es requerida")
    private LocalDate fechaExpedicion;

    /**
     * 79 = Total, 80 = Parcial (catálogo SEP)
     */
    @NotBlank(message = "El ID de tipo de certificación es requerido (79=Total, 80=Parcial)")
    private String idTipoCertificacion;

    private String tipoCertificacion;

    /**
     * Periodo escolar (ej. 2025-1, 2024-2). Para certificado parcial.
     */
    private String periodo;

    /**
     * Ciclo escolar (ej. 2024-2025)
     */
    private String cicloEscolar;

    /**
     * ID lugar expedición (catálogo SEP). Por defecto se usa idEntidadFederativa de la configuración.
     */
    private String idLugarExpedicion;

    private String lugarExpedicion;

    private String observaciones;
}
