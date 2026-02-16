package com.idee.controlescolar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para la solicitud de generación de un título electrónico.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TituloElectronicoRequest {

    @NotNull(message = "El ID del alumno es requerido")
    private Long alumnoId;

    @NotNull(message = "El ID del programa es requerido")
    private Long programaId;

    @NotNull(message = "La fecha de expedición es requerida")
    private LocalDate fechaExpedicion;

    @NotBlank(message = "El ID de modalidad de titulación es requerido")
    private String idModalidadTitulacion;

    @NotBlank(message = "La modalidad de titulación es requerida")
    private String modalidadTitulacion;

    private LocalDate fechaExamenProfesional;

    private LocalDate fechaExencionExamenProfesional;

    @NotNull(message = "El cumplimiento de servicio social es requerido")
    private Boolean cumplioServicioSocial;

    private String idFundamentoLegalServicioSocial;

    private String fundamentoLegalServicioSocial;

    // Datos de antecedente
    @NotBlank(message = "La institución de procedencia es requerida")
    private String institucionProcedencia;

    @NotBlank(message = "El ID del tipo de estudio antecedente es requerido")
    private String idTipoEstudioAntecedente;

    @NotBlank(message = "El tipo de estudio antecedente es requerido")
    private String tipoEstudioAntecedente;

    @NotBlank(message = "El ID de entidad federativa del antecedente es requerido")
    private String idEntidadFederativaAntecedente;

    @NotBlank(message = "La entidad federativa del antecedente es requerida")
    private String entidadFederativaAntecedente;

    private LocalDate fechaInicioAntecedente;

    @NotNull(message = "La fecha de terminación del antecedente es requerida")
    private LocalDate fechaTerminacionAntecedente;

    private String noCedula;

    private String observaciones;
}
