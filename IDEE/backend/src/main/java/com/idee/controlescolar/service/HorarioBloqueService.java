package com.idee.controlescolar.service;

import com.idee.controlescolar.model.HorarioBloque;
import com.idee.controlescolar.repository.HorarioBloqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Servicio para validaciones de horarios.
 * Las validaciones se ejecutan en backend para garantizar integridad
 * independientemente del frontend.
 */
@Service
@RequiredArgsConstructor
public class HorarioBloqueService {

    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioBloqueRepository horarioBloqueRepository;

    /**
     * Verifica si existe conflicto: mismo día, mismo salón y horarios que se solapan.
     * No se considera conflicto si: salones diferentes, o horarios diferentes.
     *
     * @param dia        Día de la semana
     * @param horaInicio Hora de inicio del bloque
     * @param horaFin    Hora de fin del bloque
     * @param aula       Salón/aula (texto plano, se normaliza para comparar)
     * @param excluirId  ID del bloque a excluir (para actualizaciones), null si es creación
     * @return true si hay conflicto, false si no
     */
    public boolean existeConflictoAula(HorarioBloque.DiaSemana dia,
                                      LocalTime horaInicio, LocalTime horaFin,
                                      String aula, LocalDate fechaInicio, LocalDate fechaFin,
                                      Long excluirId) {
        return buscarConflictoAula(dia, horaInicio, horaFin, aula, fechaInicio, fechaFin, excluirId).isPresent();
    }

    /**
     * Primer bloque ajeno (o distinto de {@code excluirId}) que ocupa el mismo salón el mismo día con horario solapado.
     */
    public Optional<HorarioBloque> buscarConflictoAula(HorarioBloque.DiaSemana dia,
                                                       LocalTime horaInicio, LocalTime horaFin,
                                                       String aula, LocalDate fechaInicio, LocalDate fechaFin,
                                                       Long excluirId) {
        return buscarConflictoAulaEnPrograma(null, dia, horaInicio, horaFin, aula, fechaInicio, fechaFin, excluirId);
    }

    /**
     * Igual que {@link #buscarConflictoAula} pero limitado a un programa (requerimiento: validaciones solo dentro del mismo programa).
     */
    public Optional<HorarioBloque> buscarConflictoAulaEnPrograma(Long programaId,
                                                                 HorarioBloque.DiaSemana dia,
                                                                 LocalTime horaInicio, LocalTime horaFin,
                                                                 String aula, LocalDate fechaInicio, LocalDate fechaFin,
                                                                 Long excluirId) {
        List<HorarioBloque> bloquesDia = (programaId != null)
                ? horarioBloqueRepository.findByPrograma_IdAndDiaAndEstatusOrderByHoraInicioAsc(programaId, dia, HorarioBloque.EstatusHorario.ACTIVO)
                : horarioBloqueRepository.findByDiaAndEstatusOrderByHoraInicioAsc(dia, HorarioBloque.EstatusHorario.ACTIVO);

        String aulaNorm = normalizarAula(aula);

        for (HorarioBloque b : bloquesDia) {
            if (excluirId != null && b.getId().equals(excluirId)) continue;
            if (!aulaNorm.equals(normalizarAula(b.getAula()))) continue;
            if (!fechasSeSolapan(fechaInicio, fechaFin, b.getFechaInicio(), b.getFechaFin())) continue;
            if (horariosSeSolapan(horaInicio, horaFin, b.getHoraInicio(), b.getHoraFin())) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public Optional<HorarioBloque> buscarConflictoAulaEnProgramaExcluyendoGrupo(Long programaId,
                                                                                 Long grupoIdExcluir,
                                                                                 HorarioBloque.DiaSemana dia,
                                                                                 LocalTime horaInicio, LocalTime horaFin,
                                                                                 String aula,
                                                                                 LocalDate fechaInicio, LocalDate fechaFin,
                                                                                 Long excluirId) {
        List<HorarioBloque> bloquesDia = (programaId != null)
                ? horarioBloqueRepository.findByPrograma_IdAndDiaAndEstatusOrderByHoraInicioAsc(programaId, dia, HorarioBloque.EstatusHorario.ACTIVO)
                : horarioBloqueRepository.findByDiaAndEstatusOrderByHoraInicioAsc(dia, HorarioBloque.EstatusHorario.ACTIVO);
        String aulaNorm = normalizarAula(aula);
        for (HorarioBloque b : bloquesDia) {
            if (excluirId != null && b.getId().equals(excluirId)) continue;
            if (grupoIdExcluir != null && b.getGrupoEntity() != null && grupoIdExcluir.equals(b.getGrupoEntity().getId())) continue;
            if (!aulaNorm.equals(normalizarAula(b.getAula()))) continue;
            if (!fechasSeSolapan(fechaInicio, fechaFin, b.getFechaInicio(), b.getFechaFin())) continue;
            if (horariosSeSolapan(horaInicio, horaFin, b.getHoraInicio(), b.getHoraFin())) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /**
     * Texto legible del conflicto de salón (incluye salón, día, horas y la materia que ya ocupa el espacio).
     */
    public String mensajeConflictoSalon(String asignaturaEnEdicion,
                                       HorarioBloque.DiaSemana dia,
                                       LocalTime horaInicio, LocalTime horaFin,
                                       String aulaSolicitada,
                                       HorarioBloque bloqueExistente) {
        String salon = (aulaSolicitada == null || aulaSolicitada.isBlank()) ? "(sin salón indicado)" : aulaSolicitada.trim();
        String diaTxt = dia != null ? dia.getNombre() : "—";
        String rango = horaInicio.format(HORA_FMT) + "–" + horaFin.format(HORA_FMT);
        String otraMat = bloqueExistente.getAsignatura() != null && bloqueExistente.getAsignatura().getNombre() != null
                ? bloqueExistente.getAsignatura().getNombre()
                : "otra asignatura";
        String grupoOtra = bloqueExistente.getGrupoNombre() != null && !bloqueExistente.getGrupoNombre().isBlank()
                ? bloqueExistente.getGrupoNombre()
                : (bloqueExistente.getGrupo() != null ? bloqueExistente.getGrupo() : "—");
        String rangoOtra = bloqueExistente.getHoraInicio() != null && bloqueExistente.getHoraFin() != null
                ? bloqueExistente.getHoraInicio().format(HORA_FMT) + "–" + bloqueExistente.getHoraFin().format(HORA_FMT)
                : "—";
        String matOrigen = asignaturaEnEdicion != null && !asignaturaEnEdicion.isBlank() ? asignaturaEnEdicion : "Esta asignatura";
        return matOrigen + ": conflicto de salón. El salón «" + salon + "» el " + diaTxt + " de " + rango
                + " ya está ocupado por «" + otraMat + "» (grupo " + grupoOtra + ", " + rangoOtra + "). "
                + "Cambia de salón, día u horario.";
    }

    /**
     * Verifica si ya existe un bloque activo para la misma asignatura, grupo y día.
     * No se permite crear dos bloques para la misma materia del mismo grupo el mismo día.
     */
    public boolean existeBloqueMismaAsignaturaGrupoDia(Long asignaturaId, String grupo,
                                                       HorarioBloque.DiaSemana dia,
                                                       LocalDate fechaInicio, LocalDate fechaFin,
                                                       Long excluirId) {
        return buscarBloqueMismaAsignaturaGrupoDia(asignaturaId, grupo, dia, fechaInicio, fechaFin, excluirId).isPresent();
    }

    public Optional<HorarioBloque> buscarBloqueMismaAsignaturaGrupoDia(Long asignaturaId, String grupo,
                                                                       HorarioBloque.DiaSemana dia,
                                                                       LocalDate fechaInicio, LocalDate fechaFin,
                                                                       Long excluirId) {
        if (asignaturaId == null || grupo == null || grupo.isBlank() || dia == null) return Optional.empty();
        List<HorarioBloque> existentes = horarioBloqueRepository.findByAsignatura_IdAndGrupoAndDiaAndEstatusOrderByHoraInicioAsc(
                asignaturaId, grupo, dia, HorarioBloque.EstatusHorario.ACTIVO);
        for (HorarioBloque b : existentes) {
            if (excluirId != null && b.getId().equals(excluirId)) continue;
            if (fechasSeSolapan(fechaInicio, fechaFin, b.getFechaInicio(), b.getFechaFin())) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /**
     * Duplicado asignatura+grupo+día pero limitado a un programa (evita colisiones entre programas).
     */
    public Optional<HorarioBloque> buscarBloqueMismaAsignaturaGrupoDiaEnPrograma(Long programaId,
                                                                                 Long asignaturaId, String grupo,
                                                                                 HorarioBloque.DiaSemana dia,
                                                                                 LocalDate fechaInicio, LocalDate fechaFin,
                                                                                 Long excluirId) {
        if (programaId == null) {
            return buscarBloqueMismaAsignaturaGrupoDia(asignaturaId, grupo, dia, fechaInicio, fechaFin, excluirId);
        }
        if (asignaturaId == null || grupo == null || grupo.isBlank() || dia == null) return Optional.empty();
        List<HorarioBloque> existentes = horarioBloqueRepository.findByPrograma_IdAndAsignatura_IdAndGrupoAndDiaAndEstatusOrderByHoraInicioAsc(
                programaId, asignaturaId, grupo, dia, HorarioBloque.EstatusHorario.ACTIVO);
        for (HorarioBloque b : existentes) {
            if (excluirId != null && b.getId().equals(excluirId)) continue;
            if (fechasSeSolapan(fechaInicio, fechaFin, b.getFechaInicio(), b.getFechaFin())) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public String mensajeDuplicadoMismaMateriaGrupoDia(String nombreAsignatura, String grupoNombre,
                                                        HorarioBloque.DiaSemana dia, HorarioBloque existente) {
        String diaTxt = dia != null ? dia.getNombre() : "—";
        String mat = nombreAsignatura != null && !nombreAsignatura.isBlank() ? nombreAsignatura : "Esta asignatura";
        String rangoOtra = existente.getHoraInicio() != null && existente.getHoraFin() != null
                ? existente.getHoraInicio().format(HORA_FMT) + "–" + existente.getHoraFin().format(HORA_FMT)
                : "—";
        return mat + ": ya hay otra sesión de la misma materia el " + diaTxt + " para el grupo «" + grupoNombre
                + "» (horario existente " + rangoOtra + "). No se pueden dos sesiones el mismo día; "
                + "ajusta el día o elimina el bloque duplicado.";
    }

    /**
     * Verifica si existe conflicto: mismo maestro, mismo día y horarios que se solapan.
     * Un maestro no puede impartir dos clases a la misma hora el mismo día.
     *
     * @param maestroId  ID del maestro
     * @param dia        Día de la semana
     * @param horaInicio Hora de inicio del bloque
     * @param horaFin    Hora de fin del bloque
     * @param excluirId  ID del bloque a excluir (para actualizaciones), null si es creación
     * @return true si hay conflicto, false si no
     */
    public boolean existeConflictoMaestro(Long maestroId, HorarioBloque.DiaSemana dia,
                                         LocalTime horaInicio, LocalTime horaFin,
                                         LocalDate fechaInicio, LocalDate fechaFin,
                                         Long excluirId) {
        return buscarConflictoMaestro(maestroId, dia, horaInicio, horaFin, fechaInicio, fechaFin, excluirId).isPresent();
    }

    public Optional<HorarioBloque> buscarConflictoMaestro(Long maestroId, HorarioBloque.DiaSemana dia,
                                                        LocalTime horaInicio, LocalTime horaFin,
                                                        LocalDate fechaInicio, LocalDate fechaFin,
                                                        Long excluirId) {
        return buscarConflictoMaestroEnPrograma(null, maestroId, dia, horaInicio, horaFin, fechaInicio, fechaFin, excluirId);
    }

    /**
     * Igual que {@link #buscarConflictoMaestro} pero limitado a un programa (requerimiento: validaciones solo dentro del mismo programa).
     */
    public Optional<HorarioBloque> buscarConflictoMaestroEnPrograma(Long programaId,
                                                                    Long maestroId, HorarioBloque.DiaSemana dia,
                                                                    LocalTime horaInicio, LocalTime horaFin,
                                                                    LocalDate fechaInicio, LocalDate fechaFin,
                                                                    Long excluirId) {
        List<HorarioBloque> bloquesMaestro = (programaId != null)
                ? horarioBloqueRepository.findByPrograma_IdAndDiaAndMaestro_IdAndEstatusOrderByHoraInicioAsc(programaId, dia, maestroId, HorarioBloque.EstatusHorario.ACTIVO)
                : horarioBloqueRepository.findByDiaAndMaestro_IdAndEstatusOrderByHoraInicioAsc(dia, maestroId, HorarioBloque.EstatusHorario.ACTIVO);

        for (HorarioBloque b : bloquesMaestro) {
            if (excluirId != null && b.getId().equals(excluirId)) continue;
            if (!fechasSeSolapan(fechaInicio, fechaFin, b.getFechaInicio(), b.getFechaFin())) continue;
            if (horariosSeSolapan(horaInicio, horaFin, b.getHoraInicio(), b.getHoraFin())) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public Optional<HorarioBloque> buscarConflictoMaestroEnProgramaExcluyendoGrupo(Long programaId,
                                                                                    Long grupoIdExcluir,
                                                                                    Long maestroId,
                                                                                    HorarioBloque.DiaSemana dia,
                                                                                    LocalTime horaInicio, LocalTime horaFin,
                                                                                    LocalDate fechaInicio, LocalDate fechaFin,
                                                                                    Long excluirId) {
        List<HorarioBloque> bloquesMaestro = (programaId != null)
                ? horarioBloqueRepository.findByPrograma_IdAndDiaAndMaestro_IdAndEstatusOrderByHoraInicioAsc(programaId, dia, maestroId, HorarioBloque.EstatusHorario.ACTIVO)
                : horarioBloqueRepository.findByDiaAndMaestro_IdAndEstatusOrderByHoraInicioAsc(dia, maestroId, HorarioBloque.EstatusHorario.ACTIVO);
        for (HorarioBloque b : bloquesMaestro) {
            if (excluirId != null && b.getId().equals(excluirId)) continue;
            if (grupoIdExcluir != null && b.getGrupoEntity() != null && grupoIdExcluir.equals(b.getGrupoEntity().getId())) continue;
            if (!fechasSeSolapan(fechaInicio, fechaFin, b.getFechaInicio(), b.getFechaFin())) continue;
            if (horariosSeSolapan(horaInicio, horaFin, b.getHoraInicio(), b.getHoraFin())) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public String mensajeConflictoDocente(String nombreAsignaturaEnEdicion,
                                          HorarioBloque.DiaSemana dia,
                                          LocalTime horaInicio, LocalTime horaFin,
                                          HorarioBloque claseQueLoOcup) {
        String diaTxt = dia != null ? dia.getNombre() : "—";
        String rango = horaInicio.format(HORA_FMT) + "–" + horaFin.format(HORA_FMT);
        String mat = nombreAsignaturaEnEdicion != null && !nombreAsignaturaEnEdicion.isBlank()
                ? nombreAsignaturaEnEdicion
                : "Esta asignatura";
        String otraMat = claseQueLoOcup.getAsignatura() != null && claseQueLoOcup.getAsignatura().getNombre() != null
                ? claseQueLoOcup.getAsignatura().getNombre()
                : "otra clase";
        String grupoOtra = claseQueLoOcup.getGrupoNombre() != null && !claseQueLoOcup.getGrupoNombre().isBlank()
                ? claseQueLoOcup.getGrupoNombre()
                : (claseQueLoOcup.getGrupo() != null ? claseQueLoOcup.getGrupo() : "—");
        String rangoOtra = claseQueLoOcup.getHoraInicio() != null && claseQueLoOcup.getHoraFin() != null
                ? claseQueLoOcup.getHoraInicio().format(HORA_FMT) + "–" + claseQueLoOcup.getHoraFin().format(HORA_FMT)
                : "—";
        return mat + ": conflicto de docente. El mismo maestro ya imparte «" + otraMat + "» el " + diaTxt + " de "
                + rangoOtra + " (grupo " + grupoOtra + "). Tu bloque propuesto es " + diaTxt + " " + rango
                + ". Un docente no puede estar en dos clases a la vez; cambia docente u horario.";
    }

    /**
     * Normaliza aula para comparación: trim, lowercase, null/blank → "".
     */
    private String normalizarAula(String aula) {
        if (aula == null || aula.isBlank()) return "";
        return aula.trim().toLowerCase();
    }

    /**
     * Dos rangos de fechas [aIni,aFin] y [bIni,bFin] se solapan si:
     * aIni <= bFin y bIni <= aFin, considerando null como infinito.
     */
    private boolean fechasSeSolapan(LocalDate aIni, LocalDate aFin, LocalDate bIni, LocalDate bFin) {
        LocalDate ai = aIni != null ? aIni : LocalDate.MIN;
        LocalDate af = aFin != null ? aFin : LocalDate.MAX;
        LocalDate bi = bIni != null ? bIni : LocalDate.MIN;
        LocalDate bf = bFin != null ? bFin : LocalDate.MAX;
        return !ai.isAfter(bf) && !bi.isAfter(af);
    }

    /**
     * Dos intervalos [a1,a2] y [b1,b2] se solapan si a1 < b2 y b1 < a2.
     */
    private boolean horariosSeSolapan(LocalTime a1, LocalTime a2, LocalTime b1, LocalTime b2) {
        return a1.isBefore(b2) && b1.isBefore(a2);
    }
}
