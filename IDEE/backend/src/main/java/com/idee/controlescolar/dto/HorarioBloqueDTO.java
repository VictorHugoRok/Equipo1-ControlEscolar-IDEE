package com.idee.controlescolar.dto;

import com.idee.controlescolar.model.HorarioBloque;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO para exponer HorarioBloque en API (evita serialización de proxies Hibernate).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioBloqueDTO {

    private Long id;
    private Long asignaturaId;  // Para mapeo de colores en vista maestro
    private HorarioBloque.DiaSemana dia;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String aula;
    private String grupo;
    private Long grupoId;  // FK a Grupo cuando se usa gestión correcta
    private String cicloEscolar;
    private String asignaturaNombre;
    private String asignaturaClave;
    private String programaNombre;
    private String maestroNombre;

    public static HorarioBloqueDTO from(HorarioBloque b) {
        if (b == null) return null;
        String asigNombre = b.getAsignatura() != null ? b.getAsignatura().getNombre() : null;
        String asigClave = b.getAsignatura() != null ? b.getAsignatura().getClave() : null;
        String progNombre = b.getPrograma() != null ? b.getPrograma().getNombre() : null;
        if (progNombre == null && b.getAsignatura() != null && b.getAsignatura().getPrograma() != null) {
            progNombre = b.getAsignatura().getPrograma().getNombre();
        }
        String maestroNombre = b.getMaestro() != null ? b.getMaestro().getNombreCompleto() : null;
        return HorarioBloqueDTO.builder()
                .id(b.getId())
                .asignaturaId(b.getAsignatura() != null ? b.getAsignatura().getId() : null)
                .dia(b.getDia())
                .horaInicio(b.getHoraInicio())
                .horaFin(b.getHoraFin())
                .fechaInicio(b.getFechaInicio())
                .fechaFin(b.getFechaFin())
                .aula(b.getAula())
                .grupo(b.getGrupoNombre())
                .grupoId(b.getGrupoEntity() != null ? b.getGrupoEntity().getId() : null)
                .cicloEscolar(b.getCicloEscolar())
                .asignaturaNombre(asigNombre)
                .asignaturaClave(asigClave)
                .programaNombre(progNombre)
                .maestroNombre(maestroNombre)
                .build();
    }
}
