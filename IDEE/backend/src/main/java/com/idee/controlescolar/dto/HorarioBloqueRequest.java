package com.idee.controlescolar.dto;

import com.idee.controlescolar.model.HorarioBloque;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class HorarioBloqueRequest {

    /** ID del bloque existente cuando se edita un horario completo. Null = crear bloque nuevo. */
    private Long id;

    @NotNull(message = "El programa es requerido")
    private Long programaId;

    /** ID interno de la asignatura. Si no se proporciona, se usa idAsignatura. */
    private Long asignaturaId;

    /** Identificador de negocio de la asignatura (el que tú asignas al crearla). Se usa cuando asignaturaId no está presente. */
    private String idAsignatura;

    /** Maestro que imparte la clase. */
    private Long maestroId;

    @NotNull(message = "El día es requerido")
    private HorarioBloque.DiaSemana dia;

    @NotNull(message = "La hora de inicio es requerida")
    private String horaInicio; // "HH:mm"

    @NotNull(message = "La hora de fin es requerida")
    private String horaFin; // "HH:mm"

    /** Vigencia del bloque (para modelo modular/secuencial). Null = sin límite. */
    private LocalDate fechaInicio;

    /** Vigencia del bloque (para modelo modular/secuencial). Null = sin límite. */
    private LocalDate fechaFin;

    private String grupo;       // Legacy: nombre texto. Si grupoId está presente, se ignora.
    private Long grupoId;       // Prioridad: FK a Grupo (recomendado para gestión correcta)

    private Long periodoAcademicoId;  // Catálogo periodo académico
    private String periodoIngreso;     // Código ej. "2025-1"

    private String aula; // Ej: "Lab 1"

    private HorarioBloque.EstatusHorario estatus = HorarioBloque.EstatusHorario.ACTIVO;
}
