package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Grupo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GrupoRepository extends JpaRepository<Grupo, Long> {

    /**
     * Listado de grupos para la API (gestión / inscripciones): una consulta con fetch de relaciones
     * usadas en JSON, evitando N+1. Los filtros replican la lógica previa del controlador (incl.
     * asignaturaId: grupos sin asignatura pasan el filtro).
     */
    @Query("SELECT DISTINCT g FROM Grupo g "
            + "LEFT JOIN FETCH g.alumnos "
            + "LEFT JOIN FETCH g.asignatura a "
            + "LEFT JOIN FETCH a.periodo "
            + "LEFT JOIN FETCH a.programa "
            + "LEFT JOIN FETCH g.programa "
            + "LEFT JOIN FETCH g.periodoAcademico "
            + "LEFT JOIN FETCH g.maestro "
            + "WHERE (:maestroId IS NULL OR (g.maestro IS NOT NULL AND g.maestro.id = :maestroId)) "
            + "AND (:programaId IS NULL OR g.programa.id = :programaId "
            + "     OR (a IS NOT NULL AND a.programa IS NOT NULL AND a.programa.id = :programaId)) "
            + "AND (:periodoAcademicoId IS NULL OR (g.periodoAcademico IS NOT NULL AND g.periodoAcademico.id = :periodoAcademicoId)) "
            + "AND (:periodoNumero IS NULL OR ("
            + "     (a IS NOT NULL AND a.periodo IS NOT NULL AND a.periodo.numero IS NOT NULL AND a.periodo.numero = :periodoNumero) "
            + "     OR ((a IS NULL OR a.periodo IS NULL OR a.periodo.numero IS NULL) "
            + "         AND g.numeroPeriodo IS NOT NULL AND g.numeroPeriodo = :periodoNumero))) "
            + "AND (:asignaturaId IS NULL OR g.asignatura IS NULL OR g.asignatura.id = :asignaturaId)")
    List<Grupo> findAllForApiList(
            @Param("maestroId") Long maestroId,
            @Param("programaId") Long programaId,
            @Param("periodoAcademicoId") Long periodoAcademicoId,
            @Param("periodoNumero") Integer periodoNumero,
            @Param("asignaturaId") Long asignaturaId);

    /**
     * Grupos activos con al menos un alumno inscrito (tabla grupo_alumno).
     * Para el desplegable de revisión de calificaciones del personal administrativo.
     */
    @Query("SELECT DISTINCT g FROM Grupo g "
            + "JOIN g.alumnos al "
            + "LEFT JOIN FETCH g.alumnos "
            + "LEFT JOIN FETCH g.asignatura a "
            + "LEFT JOIN FETCH a.periodo "
            + "LEFT JOIN FETCH a.programa "
            + "LEFT JOIN FETCH g.programa "
            + "LEFT JOIN FETCH g.periodoAcademico "
            + "LEFT JOIN FETCH g.maestro "
            + "WHERE g.estatus = com.idee.controlescolar.model.Grupo$EstatusGrupo.ACTIVO")
    List<Grupo> findActivosConAlumnosInscritos();

    /**
     * Grupos activos con alumnos y al menos un {@link com.idee.controlescolar.model.HorarioBloque} activo con materia.
     * Desplegable de calificaciones (admin): mismo grupo puede tener varias materias vía horarios.
     */
    @Query("SELECT DISTINCT g FROM Grupo g "
            + "JOIN g.alumnos al "
            + "LEFT JOIN FETCH g.alumnos "
            + "LEFT JOIN FETCH g.asignatura a "
            + "LEFT JOIN FETCH a.periodo "
            + "LEFT JOIN FETCH a.programa "
            + "LEFT JOIN FETCH g.programa "
            + "LEFT JOIN FETCH g.periodoAcademico "
            + "LEFT JOIN FETCH g.maestro "
            + "WHERE g.estatus = com.idee.controlescolar.model.Grupo$EstatusGrupo.ACTIVO "
            + "AND EXISTS (SELECT 1 FROM HorarioBloque h WHERE h.grupoEntity = g "
            + "  AND h.estatus = com.idee.controlescolar.model.HorarioBloque$EstatusHorario.ACTIVO "
            + "  AND h.asignatura IS NOT NULL)")
    List<Grupo> findActivosConAlumnosYClaseEnHorario();

    @EntityGraph(attributePaths = {"asignatura", "asignatura.programa", "asignatura.periodo", "programa", "periodoAcademico", "maestro", "alumnos"})
    List<Grupo> findByMaestroId(Long maestroId);

    List<Grupo> findByAsignaturaId(Long asignaturaId);

    /** Grupo básico: mismo nombre, programa y ciclo */
    boolean existsByNombreAndProgramaIdAndCicloEscolar(String nombre, Long programaId, String cicloEscolar);

    /** Grupo básico sin periodo: mismo nombre y programa (grupos por programa únicamente) */
    @Query("SELECT COUNT(g) > 0 FROM Grupo g WHERE g.nombre = :nombre AND g.programa.id = :programaId AND g.periodoAcademico IS NULL AND g.asignatura IS NULL")
    boolean existsByNombreAndProgramaIdSinPeriodo(@Param("nombre") String nombre, @Param("programaId") Long programaId);

    @Query("SELECT COUNT(g) > 0 FROM Grupo g WHERE g.nombre = :nombre AND g.programa.id = :programaId AND g.numeroPeriodo = :num AND g.asignatura IS NULL")
    boolean existsByNombreAndProgramaIdAndNumeroPeriodoBasico(@Param("nombre") String nombre, @Param("programaId") Long programaId, @Param("num") Integer num);

    @Query("SELECT COUNT(g) > 0 FROM Grupo g WHERE g.nombre = :nombre AND g.programa.id = :programaId AND g.numeroPeriodo = :num AND g.asignatura IS NULL AND g.id <> :excluirId")
    boolean existsOtroBasicoMismoNombreProgramaPeriodo(@Param("nombre") String nombre, @Param("programaId") Long programaId, @Param("num") Integer num, @Param("excluirId") Long excluirId);

    /** Grupo avanzado: mismo nombre y asignatura */
    boolean existsByNombreAndAsignaturaId(String nombre, Long asignaturaId);

    /** Unicidad global: no puede haber dos grupos con el mismo nombre en todo el sistema */
    @Query("SELECT COUNT(g) > 0 FROM Grupo g WHERE LOWER(TRIM(g.nombre)) = LOWER(TRIM(:nombre))")
    boolean existsByNombre(@Param("nombre") String nombre);

    /** Para validar en actualización: existe otro grupo con mismo nombre (excluyendo id) */
    @Query("SELECT COUNT(g) > 0 FROM Grupo g WHERE LOWER(TRIM(g.nombre)) = LOWER(TRIM(:nombre)) AND g.id <> :excluirId")
    boolean existsByNombreAndIdNot(@Param("nombre") String nombre, @Param("excluirId") Long excluirId);

    /** Grupos en los que está inscrito un alumno */
    @EntityGraph(attributePaths = {"asignatura", "asignatura.programa", "programa"})
    List<Grupo> findByAlumnos_Id(Long alumnoId);

    @Query("SELECT DISTINCT g.maestro.id FROM Grupo g WHERE g.maestro IS NOT NULL")
    Set<Long> findDistinctMaestroIdsConGrupoAsignado();

    @Query("SELECT DISTINCT g.maestro.id FROM Grupo g "
            + "LEFT JOIN g.asignatura a "
            + "WHERE g.maestro IS NOT NULL "
            + "AND ("
            + "  (g.programa IS NOT NULL AND g.programa.id IN :programaIds) "
            + "  OR (a IS NOT NULL AND a.programa IS NOT NULL AND a.programa.id IN :programaIds)"
            + ")")
    Set<Long> findDistinctMaestroIdsConGrupoAsignadoEnProgramas(@Param("programaIds") Collection<Long> programaIds);

    @Query("SELECT DISTINCT g FROM Grupo g "
            + "LEFT JOIN FETCH g.alumnos "
            + "LEFT JOIN FETCH g.asignatura a "
            + "LEFT JOIN FETCH a.periodo "
            + "LEFT JOIN FETCH a.programa "
            + "LEFT JOIN FETCH g.programa "
            + "LEFT JOIN FETCH g.periodoAcademico "
            + "LEFT JOIN FETCH g.maestro "
            + "WHERE g.id = :id")
    Optional<Grupo> findWithDetailsForInscripcion(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Grupo g SET g.periodoAcademico = null WHERE g.periodoAcademico.id IN :ids")
    void clearPeriodoByPeriodoIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT COUNT(a) FROM Grupo g JOIN g.alumnos a WHERE g.id = :grupoId")
    long countAlumnosByGrupoId(@Param("grupoId") Long grupoId);
}
