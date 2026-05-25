package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Calificacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repositorio para calificaciones
 */
@Repository
public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {

    /**
     * Buscar calificaciones por ID de alumno
     */
    @EntityGraph(attributePaths = {
            "alumno", "asignatura", "grupo",
            "criterios", "criterios.criterio"
    })
    List<Calificacion> findByAlumnoId(Long alumnoId);

    @Override
    @EntityGraph(attributePaths = {
            "alumno", "asignatura", "grupo",
            "criterios", "criterios.criterio"
    })
    List<Calificacion> findAll();

    boolean existsByAlumno_IdAndGrupo_IdAndAsignatura_IdAndEstadoAprobacion(
            Long alumnoId, Long grupoId, Long asignaturaId, Calificacion.EstadoAprobacion estado);

    @Query("SELECT DISTINCT g.maestro.id FROM Calificacion c "
            + "JOIN c.grupo g "
            + "WHERE c.alumno.id = :alumnoId "
            + "AND c.estadoAprobacion = com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CONFIRMADA "
            + "AND g.maestro IS NOT NULL")
    Set<Long> findDistinctMaestroIdsConCalificacionConfirmadaDelAlumno(@Param("alumnoId") Long alumnoId);

    @Query("SELECT COUNT(c) > 0 FROM Calificacion c "
            + "JOIN c.grupo g "
            + "WHERE g.maestro IS NOT NULL AND g.maestro.id = :maestroId "
            + "AND c.estadoAprobacion IN ("
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CAPTURADA, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.EN_REVISION, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CONFIRMADA)")
    boolean existsByMaestroConCalificacionesEnviadasOConfirmadas(@Param("maestroId") Long maestroId);

    /**
     * El docente capturó/envió calificaciones del módulo (grupo + asignatura), requisito para ver resultados de evaluación de alumnos.
     */
    @Query("SELECT COUNT(c) > 0 FROM Calificacion c JOIN c.grupo g "
            + "WHERE g.id = :grupoId AND g.maestro IS NOT NULL AND g.maestro.id = :maestroId "
            + "AND c.asignatura.id = :asignaturaId "
            + "AND c.estadoAprobacion IN ("
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CAPTURADA, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.EN_REVISION, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CONFIRMADA)")
    boolean existsEnviadaOConfirmadaPorGrupoAsignaturaYMaestro(
            @Param("grupoId") Long grupoId,
            @Param("asignaturaId") Long asignaturaId,
            @Param("maestroId") Long maestroId);

    /**
     * Al menos una calificación capturada/en revisión/confirmada para grupo + asignatura.
     * No exige {@link Grupo#getMaestro()}: en muchos datos el docente solo figura en {@link com.idee.controlescolar.model.HorarioBloque}.
     * Quien llama debe haber comprobado ya que el módulo (horario) pertenece al maestro.
     */
    @Query("SELECT COUNT(c) > 0 FROM Calificacion c "
            + "WHERE c.grupo.id = :grupoId AND c.asignatura.id = :asignaturaId "
            + "AND c.estadoAprobacion IN ("
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CAPTURADA, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.EN_REVISION, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CONFIRMADA)")
    boolean existsEnviadaOConfirmadaPorGrupoYAsignatura(
            @Param("grupoId") Long grupoId,
            @Param("asignaturaId") Long asignaturaId);

    /**
     * Alumnos con calificación registrada (capturada/en revisión/confirmada) en el mismo grupo y asignatura.
     * Sirve para alinear “cuántos deben evaluar” con quienes tienen calificación en ese módulo (no todo el grupo escolar genérico).
     */
    @Query("SELECT COUNT(DISTINCT c.alumno.id) FROM Calificacion c "
            + "WHERE c.grupo.id = :grupoId AND c.asignatura.id = :asignaturaId "
            + "AND c.estadoAprobacion IN ("
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CAPTURADA, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.EN_REVISION, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CONFIRMADA)")
    long countDistinctAlumnosConCalificacionEnModulo(
            @Param("grupoId") Long grupoId,
            @Param("asignaturaId") Long asignaturaId);

    /**
     * Alumnos del grupo con calificación de la asignatura ya enviada a revisión o confirmada por secretaría.
     */
    @Query("SELECT COUNT(DISTINCT c.alumno.id) FROM Calificacion c "
            + "WHERE c.grupo.id = :grupoId AND c.asignatura.id = :asignaturaId "
            + "AND c.estadoAprobacion IN ("
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.EN_REVISION, "
            + " com.idee.controlescolar.model.Calificacion$EstadoAprobacion.CONFIRMADA)")
    long countDistinctAlumnosModuloEnRevisionOConfirmada(
            @Param("grupoId") Long grupoId,
            @Param("asignaturaId") Long asignaturaId);

    /**
     * Calificaciones del alumno con periodo académico y asignatura (periodo del plan) para ordenar historial kardex.
     */
    @Query("SELECT c FROM Calificacion c "
            + "LEFT JOIN FETCH c.periodoAcademico "
            + "LEFT JOIN FETCH c.asignatura a "
            + "LEFT JOIN FETCH a.periodo "
            + "WHERE c.alumno.id = :alumnoId")
    List<Calificacion> findByAlumnoIdForKardexHistorial(@Param("alumnoId") Long alumnoId);

    /**
     * Periodos distintos usados en calificaciones (para filtro periodo de ingreso).
     */
    @Query("SELECT DISTINCT c.periodo FROM Calificacion c WHERE c.periodo IS NOT NULL ORDER BY c.periodo")
    List<String> findDistinctPeriodos();

    /**
     * IDs de alumnos cuyo periodo de ingreso (min periodo) coincide con el dado.
     */
    @Query(value = "SELECT alumno_id FROM calificaciones WHERE periodo IS NOT NULL GROUP BY alumno_id HAVING MIN(periodo) = :periodo", nativeQuery = true)
    List<Long> findAlumnoIdsByPeriodoIngreso(@Param("periodo") String periodo);

    /**
     * Mapeo alumno_id -> periodo de ingreso (min periodo por alumno).
     * Retorna filas [alumno_id, periodo_ingreso].
     */
    @Query(value = "SELECT alumno_id, MIN(periodo) as periodo_ingreso FROM calificaciones WHERE periodo IS NOT NULL GROUP BY alumno_id", nativeQuery = true)
    List<Object[]> findAlumnoIdToPeriodoIngreso();

    /**
     * Buscar calificaciones por alumno y periodo (ej. "2025-1").
     * Usado para certificados parciales/totales por ciclo.
     */
    List<Calificacion> findByAlumnoIdAndPeriodo(Long alumnoId, String periodo);

    /**
     * Última calificación registrada para (alumno, asignatura).
     * Se usa para upsert en capturas masivas (certificados).
     */
    Optional<Calificacion> findTop1ByAlumno_IdAndAsignatura_IdOrderByFechaCreacionDesc(Long alumnoId, Long asignaturaId);

    /**
     * Buscar por periodo flexible: coincide contra c.periodo o c.periodoAcademico.codigo.
     * Esto evita que calificaciones guardadas con periodoAcademico se "pierdan" en filtros.
     */
    @EntityGraph(attributePaths = {
            "alumno", "asignatura", "grupo",
            "criterios", "criterios.criterio"
    })
    @Query("SELECT c FROM Calificacion c " +
            "LEFT JOIN c.periodoAcademico pa " +
            "WHERE c.alumno.id = :alumnoId " +
            "AND (" +
            "   (c.periodo IS NOT NULL AND c.periodo = :periodo) " +
            "   OR (pa IS NOT NULL AND pa.codigo = :periodo)" +
            ")")
    List<Calificacion> findByAlumnoIdAndPeriodoFlexible(@Param("alumnoId") Long alumnoId,
                                                        @Param("periodo") String periodo);

    @EntityGraph(attributePaths = {
            "alumno", "asignatura", "grupo",
            "criterios", "criterios.criterio"
    })
    List<Calificacion> findByGrupo_Maestro_Id(Long maestroId);

    @EntityGraph(attributePaths = {
            "alumno", "asignatura", "grupo",
            "criterios", "criterios.criterio"
    })
    List<Calificacion> findByGrupo_IdAndAsignatura_Id(Long grupoId, Long asignaturaId);

    List<Calificacion> findByAlumnoIdAndAsignaturaId(Long alumnoId, Long asignaturaId);

    List<Calificacion> findByGrupo_Id(Long grupoId);

    boolean existsByGrupo_Id(Long grupoId);

    boolean existsByAlumno_IdAndGrupo_Id(Long alumnoId, Long grupoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Calificacion c SET c.periodoAcademico = null WHERE c.periodoAcademico.id IN :ids")
    void clearPeriodoByPeriodoIds(@Param("ids") Collection<Long> ids);
}
