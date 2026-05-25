package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlumnoDocumentoMeta {
    private Long id;
    private String tipo;
    private String descripcion;
    private Integer docSlot;
    private String etiquetaDocumento;
    private String numeroCedula;
    private Boolean entregado;
    private LocalDate fechaRecepcion;
    private String filename;
    /** Nombre del enum {@link com.idee.controlescolar.model.DocumentoAlumno.OrigenExpediente}. */
    private String origenUltimaCarga;
    private Long cargadoPorUsuarioId;
}
