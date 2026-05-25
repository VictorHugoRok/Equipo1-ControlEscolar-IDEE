package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.EvaluacionDocenteItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvaluacionDocenteItemRepository extends JpaRepository<EvaluacionDocenteItem, Long> {

    /** Solo respuestas de alumnos (excluye autoevaluación: respuesta.alumno IS NOT NULL). */
    @Query("SELECT i.pregunta.id, AVG(i.valor), COUNT(DISTINCT i.respuesta.id) FROM EvaluacionDocenteItem i "
            + "WHERE i.maestro.id = :maestroId AND i.respuesta.formulario.id = :formularioId "
            + "AND i.respuesta.alumno IS NOT NULL "
            + "GROUP BY i.pregunta.id ORDER BY i.pregunta.id")
    List<Object[]> estadisticasAnonimasPorMaestroYFormulario(
            @Param("maestroId") Long maestroId,
            @Param("formularioId") Long formularioId);

    @Query("SELECT i.pregunta.id, AVG(i.valor), COUNT(DISTINCT i.respuesta.id) FROM EvaluacionDocenteItem i "
            + "WHERE i.maestro.id = :maestroId AND i.respuesta.formulario.id = :formularioId "
            + "AND i.respuesta.horarioBloque.id = :horarioBloqueId "
            + "AND i.respuesta.alumno IS NOT NULL "
            + "GROUP BY i.pregunta.id ORDER BY i.pregunta.id")
    List<Object[]> estadisticasAnonimasPorMaestroFormularioYBloque(
            @Param("maestroId") Long maestroId,
            @Param("formularioId") Long formularioId,
            @Param("horarioBloqueId") Long horarioBloqueId);

    @Query("SELECT i.pregunta.id, i.valorTexto FROM EvaluacionDocenteItem i "
            + "WHERE i.maestro.id = :maestroId AND i.respuesta.formulario.id = :formularioId "
            + "AND i.respuesta.alumno IS NOT NULL "
            + "AND i.valorTexto IS NOT NULL")
    List<Object[]> respuestasTextoAnonimasPorMaestroYFormulario(
            @Param("maestroId") Long maestroId,
            @Param("formularioId") Long formularioId);

    @Query("SELECT i.pregunta.id, i.valorTexto FROM EvaluacionDocenteItem i "
            + "WHERE i.maestro.id = :maestroId AND i.respuesta.formulario.id = :formularioId "
            + "AND i.respuesta.horarioBloque.id = :horarioBloqueId "
            + "AND i.respuesta.alumno IS NOT NULL "
            + "AND i.valorTexto IS NOT NULL")
    List<Object[]> respuestasTextoAnonimasPorMaestroFormularioYBloque(
            @Param("maestroId") Long maestroId,
            @Param("formularioId") Long formularioId,
            @Param("horarioBloqueId") Long horarioBloqueId);

    /**
     * Respuestas Likert de la autoevaluación del docente en un bloque (formulario tipo AUTOEVALUACION).
     */
    @Query("SELECT i.pregunta.id, i.pregunta.orden, i.valor FROM EvaluacionDocenteItem i "
            + "JOIN i.respuesta r JOIN r.formulario fo "
            + "WHERE r.evaluadorUsuario.id = :usuarioId "
            + "AND r.horarioBloque.id = :horarioBloqueId "
            + "AND fo.tipo = com.idee.controlescolar.model.EvaluacionDocenteFormulario$TipoEvaluacion.AUTOEVALUACION "
            + "AND i.valor IS NOT NULL")
    List<Object[]> findAutoevalLikertValoresPorUsuarioYHorario(
            @Param("usuarioId") Long usuarioId,
            @Param("horarioBloqueId") Long horarioBloqueId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EvaluacionDocenteItem i WHERE i.respuesta.formulario.id = :formularioId")
    int deleteByFormularioId(@Param("formularioId") Long formularioId);
}
