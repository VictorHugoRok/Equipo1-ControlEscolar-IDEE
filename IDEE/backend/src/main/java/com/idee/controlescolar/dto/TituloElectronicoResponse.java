package com.idee.controlescolar.dto;

import com.idee.controlescolar.model.EstatusTitulo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para la respuesta de un título electrónico.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TituloElectronicoResponse {

    private Long id;
    private String folioControl;

    // Datos del alumno
    private Long alumnoId;
    private String alumnoMatricula;
    private String alumnoNombreCompleto;
    private String alumnoCurp;
    private String alumnoCorreo;

    // Datos del programa
    private Long programaId;
    private String programaClave;
    private String programaNombre;
    private String programaRvoe;

    // Datos de expedición
    private LocalDate fechaExpedicion;
    private String modalidadTitulacion;
    private LocalDate fechaExamenProfesional;
    private LocalDate fechaExencionExamenProfesional;

    // Servicio social
    private Boolean cumplioServicioSocial;
    private String fundamentoLegalServicioSocial;

    // Antecedente
    private String institucionProcedencia;
    private String tipoEstudioAntecedente;
    private LocalDate fechaTerminacionAntecedente;

    // Estado y archivos
    private EstatusTitulo estatus;
    private String estatusDescripcion;
    private String xmlPath;
    private boolean tieneSello;
    private boolean estaCompleto;

    private String observaciones;

    // Auditoría
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String usuarioCreacion;
}
