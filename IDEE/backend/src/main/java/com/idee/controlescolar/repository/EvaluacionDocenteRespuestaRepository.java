package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.EvaluacionDocenteRespuesta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvaluacionDocenteRespuestaRepository extends JpaRepository<EvaluacionDocenteRespuesta, Long> {

    boolean existsByFormulario_IdAndAlumno_Id(Long formularioId, Long alumnoId);

    Optional<EvaluacionDocenteRespuesta> findByFormulario_IdAndAlumno_Id(Long formularioId, Long alumnoId);

    boolean existsByFormulario_IdAndAlumno_IdAndHorarioBloque_Id(Long formularioId, Long alumnoId, Long horarioBloqueId);

    boolean existsByFormulario_IdAndEvaluadorUsuario_IdAndMaestroEvaluado_Id(Long formularioId, Long evaluadorUsuarioId, Long maestroEvaluadoId);

    boolean existsByFormulario_IdAndEvaluadorUsuario_IdAndHorarioBloque_Id(Long formularioId, Long evaluadorUsuarioId, Long horarioBloqueId);

    boolean existsByFormulario_IdAndEvaluadorUsuario_Id(Long formularioId, Long evaluadorUsuarioId);

    boolean existsByFormulario_Id(Long formularioId);

    boolean existsByHorarioBloque_Id(Long horarioBloqueId);

    Optional<EvaluacionDocenteRespuesta> findByFormulario_IdAndEvaluadorUsuario_Id(Long formularioId, Long evaluadorUsuarioId);

    Optional<EvaluacionDocenteRespuesta> findByFormulario_IdAndEvaluadorUsuario_IdAndMaestroEvaluado_Id(
            Long formularioId, Long evaluadorUsuarioId, Long maestroEvaluadoId);

    Optional<EvaluacionDocenteRespuesta> findByFormulario_IdAndEvaluadorUsuario_IdAndHorarioBloque_Id(
            Long formularioId, Long evaluadorUsuarioId, Long horarioBloqueId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EvaluacionDocenteRespuesta r WHERE r.formulario.id = :formularioId")
    int deleteByFormularioId(@Param("formularioId") Long formularioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EvaluacionDocenteRespuesta r SET r.horarioBloque = null WHERE r.horarioBloque.id = :horarioBloqueId")
    int clearHorarioBloqueId(@Param("horarioBloqueId") Long horarioBloqueId);

    /**
     * Evaluaciones académicas de un docente en un formulario (puede haber varias, una por clase/horario).
     */
    @Query("SELECT r FROM EvaluacionDocenteRespuesta r JOIN r.formulario f "
            + "WHERE f.id = :formId AND f.tipo = com.idee.controlescolar.model.EvaluacionDocenteFormulario$TipoEvaluacion.POR_SECRETARIA_ACADEMICA "
            + "AND r.maestroEvaluado.id = :maestroId ORDER BY r.id ASC")
    List<EvaluacionDocenteRespuesta> findEvaluacionesAcademicasPorFormularioYDocente(
            @Param("formId") Long formId, @Param("maestroId") Long maestroId);

    /**
     * Evaluación académica de un docente para una clase concreta (mismo formulario).
     */
    @Query("SELECT r FROM EvaluacionDocenteRespuesta r JOIN r.formulario f "
            + "WHERE f.id = :formId AND f.tipo = com.idee.controlescolar.model.EvaluacionDocenteFormulario$TipoEvaluacion.POR_SECRETARIA_ACADEMICA "
            + "AND r.maestroEvaluado.id = :maestroId AND r.horarioBloque.id = :hid")
    List<EvaluacionDocenteRespuesta> findEvaluacionesAcademicasPorFormularioMaestroYHorario(
            @Param("formId") Long formId, @Param("maestroId") Long maestroId, @Param("hid") Long horarioBloqueId);

    /**
     * Informes / evaluaciones académicas donde este maestro fue evaluado.
     */
    @Query("SELECT r FROM EvaluacionDocenteRespuesta r JOIN FETCH r.formulario f "
            + "LEFT JOIN FETCH r.horarioBloque h "
            + "WHERE r.maestroEvaluado.id = :maestroId AND f.tipo = com.idee.controlescolar.model.EvaluacionDocenteFormulario$TipoEvaluacion.POR_SECRETARIA_ACADEMICA "
            + "ORDER BY r.fechaVisita DESC NULLS LAST, r.id DESC")
    List<EvaluacionDocenteRespuesta> findEvaluacionesAcademicasPorMaestroEvaluado(@Param("maestroId") Long maestroId);

    @Query("SELECT COUNT(r) FROM EvaluacionDocenteRespuesta r JOIN r.formulario f "
            + "WHERE r.maestroEvaluado.id = :maestroId "
            + "AND f.tipo = com.idee.controlescolar.model.EvaluacionDocenteFormulario$TipoEvaluacion.POR_SECRETARIA_ACADEMICA "
            + "AND r.informeParaDocente IS NOT NULL "
            + "AND r.informeLeidoEn IS NULL")
    long countInformesAcademicosSinLeerParaMaestro(@Param("maestroId") Long maestroId);

    /**
     * Carga ítems y relaciones; {@code observacionesBloque} se inicializa en segunda consulta (lazy)
     * para evitar {@code MultipleBagFetchException} (dos colecciones tipo bag en el mismo fetch).
     */
    @EntityGraph(attributePaths = {
            "items", "items.pregunta",
            "horarioBloque", "horarioBloque.asignatura", "horarioBloque.grupoEntity",
            "evaluadorUsuario", "evaluadorUsuario.personal"
    })
    @Query("SELECT r FROM EvaluacionDocenteRespuesta r WHERE r.id = :id")
    Optional<EvaluacionDocenteRespuesta> findByIdWithItems(@Param("id") Long id);
}
