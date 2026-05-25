package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.HorarioBloque;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface HorarioBloqueRepository extends JpaRepository<HorarioBloque, Long> {

    @EntityGraph(attributePaths = {"grupoEntity", "grupoEntity.maestro", "asignatura", "maestro", "periodoAcademico"})
    List<HorarioBloque> findByGrupoEntity_IdInAndEstatus(Collection<Long> grupoIds, HorarioBloque.EstatusHorario estatus);

    @EntityGraph(attributePaths = {"grupoEntity", "grupoEntity.maestro", "asignatura", "maestro", "periodoAcademico"})
    List<HorarioBloque> findByMaestro_IdAndEstatus(Long maestroId, HorarioBloque.EstatusHorario estatus);

    List<HorarioBloque> findByProgramaIdOrderByDiaAscHoraInicioAsc(Long programaId);

    // --- Soft delete / listado solo activos ---
    List<HorarioBloque> findByGrupoEntity_IdAndEstatusOrderByDiaAscHoraInicioAsc(Long grupoId, HorarioBloque.EstatusHorario estatus);

    List<HorarioBloque> findByGrupoEntity_IdAndPeriodoAcademico_IdAndEstatusOrderByDiaAscHoraInicioAsc(
            Long grupoId, Long periodoAcademicoId, HorarioBloque.EstatusHorario estatus);

    List<HorarioBloque> findByPrograma_IdAndEstatusOrderByDiaAscHoraInicioAsc(Long programaId, HorarioBloque.EstatusHorario estatus);

    List<HorarioBloque> findAllByEstatusOrderByDiaAscHoraInicioAsc(HorarioBloque.EstatusHorario estatus);

    List<HorarioBloque> findAllByOrderByDiaAscHoraInicioAsc();

    /**
     * Bloques asignados al maestro en la pantalla de horarios (secretaría académica).
     */
    @EntityGraph(attributePaths = {"programa", "asignatura", "maestro"})
    List<HorarioBloque> findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(Long maestroId, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos de un día, para validación de conflictos de aula.
     */
    List<HorarioBloque> findByDiaAndEstatusOrderByHoraInicioAsc(HorarioBloque.DiaSemana dia, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos de un día dentro de un programa (validación de conflictos por programa).
     */
    List<HorarioBloque> findByPrograma_IdAndDiaAndEstatusOrderByHoraInicioAsc(Long programaId, HorarioBloque.DiaSemana dia, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos de un maestro en un día, para validación de conflictos de horario.
     */
    List<HorarioBloque> findByDiaAndMaestro_IdAndEstatusOrderByHoraInicioAsc(HorarioBloque.DiaSemana dia, Long maestroId, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos de un maestro en un día dentro de un programa (validación de conflictos por programa).
     */
    List<HorarioBloque> findByPrograma_IdAndDiaAndMaestro_IdAndEstatusOrderByHoraInicioAsc(Long programaId, HorarioBloque.DiaSemana dia, Long maestroId, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos por grupo y estatus (para filtrar horarios del alumno).
     */
    @EntityGraph(attributePaths = {"programa", "asignatura", "maestro"})
    List<HorarioBloque> findByGrupoInAndEstatusOrderByDiaAscHoraInicioAsc(
            Collection<String> grupoNombres, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos por IDs reales de grupo. Usar esto para portal de alumno evita cruces
     * cuando dos programas tienen grupos con el mismo nombre.
     */
    @EntityGraph(attributePaths = {"programa", "asignatura", "maestro", "grupoEntity", "periodoAcademico"})
    List<HorarioBloque> findByGrupoEntity_IdInAndEstatusOrderByDiaAscHoraInicioAsc(
            Collection<Long> grupoIds, HorarioBloque.EstatusHorario estatus);

    /**
     * Verifica si ya existe un bloque activo para la misma asignatura, grupo y día.
     */
    boolean existsByAsignatura_IdAndGrupoAndDiaAndEstatus(
            Long asignaturaId, String grupo, HorarioBloque.DiaSemana dia, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos para la misma asignatura, grupo y día (para validar con vigencia).
     */
    List<HorarioBloque> findByAsignatura_IdAndGrupoAndDiaAndEstatusOrderByHoraInicioAsc(
            Long asignaturaId, String grupo, HorarioBloque.DiaSemana dia, HorarioBloque.EstatusHorario estatus);

    /**
     * Bloques activos para la misma asignatura, grupo y día dentro de un programa (evita colisiones de nombres de grupo entre programas).
     */
    List<HorarioBloque> findByPrograma_IdAndAsignatura_IdAndGrupoAndDiaAndEstatusOrderByHoraInicioAsc(
            Long programaId, Long asignaturaId, String grupo, HorarioBloque.DiaSemana dia, HorarioBloque.EstatusHorario estatus);

    /**
     * Verifica si ya existe otro bloque activo (excluyendo id) para la misma asignatura, grupo y día.
     */
    boolean existsByAsignatura_IdAndGrupoAndDiaAndEstatusAndIdNot(
            Long asignaturaId, String grupo, HorarioBloque.DiaSemana dia, HorarioBloque.EstatusHorario estatus, Long excluirId);

    /**
     * Bloques por grupo y periodo académico (para reemplazar horario al guardar desde visual).
     */
    List<HorarioBloque> findByGrupoEntity_IdAndPeriodoAcademico_Id(Long grupoId, Long periodoAcademicoId);

    /**
     * Bloques que referencian un grupo (para eliminar antes de borrar el grupo).
     */
    List<HorarioBloque> findByGrupoEntity_Id(Long grupoId);

    /**
     * Bloques activos de un grupo (revisión de calificaciones admin: materias asignadas por horario).
     */
    @EntityGraph(attributePaths = {
            "asignatura", "asignatura.programa", "maestro", "grupoEntity", "grupoEntity.programa",
            "periodoAcademico", "programa"
    })
    List<HorarioBloque> findByGrupoEntity_IdAndEstatus(Long grupoId, HorarioBloque.EstatusHorario estatus);

    @Query("SELECT DISTINCT h.maestro.id FROM HorarioBloque h WHERE h.maestro IS NOT NULL")
    Set<Long> findDistinctMaestroIdsConClaseAsignada();

    @Query("SELECT DISTINCT h.maestro.id FROM HorarioBloque h "
            + "WHERE h.maestro IS NOT NULL "
            + "AND h.programa IS NOT NULL AND h.programa.id IN :programaIds")
    Set<Long> findDistinctMaestroIdsConClaseAsignadaEnProgramas(@Param("programaIds") Collection<Long> programaIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE HorarioBloque h SET h.periodoAcademico = null WHERE h.periodoAcademico.id IN :ids")
    void clearPeriodoByPeriodoIds(@Param("ids") Collection<Long> ids);
}
