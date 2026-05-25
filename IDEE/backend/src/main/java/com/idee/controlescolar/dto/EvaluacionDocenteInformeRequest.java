package com.idee.controlescolar.dto;

import lombok.Data;

@Data
public class EvaluacionDocenteInformeRequest {
    private Long formularioId;
    private Long maestroId;
    /** Clase/módulo evaluado: el informe es independiente por horario. */
    private Long horarioBloqueId;
    /** Texto del informe visible para el docente (máx. definido en servicio). */
    private String informeParaDocente;
}
