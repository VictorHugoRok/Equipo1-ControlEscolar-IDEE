package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Alumno;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Repositorio para gestionar alumnos.
 */
@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

    /**
     * Busca alumnos por nombre, apellidos, matrícula o CURP (criterio flexible).
     */
    @EntityGraph(Alumno.GRAPH_PROGRAMAS_INSCRIPCION)
    @Query("SELECT a FROM Alumno a WHERE LOWER(CONCAT(COALESCE(a.nombre,''), ' ', COALESCE(a.apellidoPaterno,''), ' ', COALESCE(a.apellidoMaterno,''))) LIKE LOWER(CONCAT('%', :criterio, '%')) " +
            "OR LOWER(a.matricula) LIKE LOWER(CONCAT('%', :criterio, '%')) " +
            "OR LOWER(a.curp) LIKE LOWER(CONCAT('%', :criterio, '%'))")
    List<Alumno> buscarPorCriterio(@Param("criterio") String criterio);

    /**
     * Busca un alumno por matrícula con inscripciones a programas (alumno_programa) cargadas.
     */
    @EntityGraph(Alumno.GRAPH_PROGRAMAS_INSCRIPCION)
    Optional<Alumno> findByMatricula(String matricula);

    /**
     * Busca un alumno por CURP con inscripciones a programas cargadas.
     */
    @EntityGraph(Alumno.GRAPH_PROGRAMAS_INSCRIPCION)
    Optional<Alumno> findByCurp(String curp);

    /**
     * Verifica si existe un alumno con la matrícula especificada
     */
    boolean existsByMatricula(String matricula);

    @EntityGraph(Alumno.GRAPH_PROGRAMAS_INSCRIPCION)
    Optional<Alumno> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {
            "documentos",
            "programasAsignados",
            "programasAsignados.programa",
            "programasAsignados.periodoIngreso",
            "programasAsignados.periodoAcademicoActual"
    })
    @Query("SELECT a FROM Alumno a WHERE a.usuario.id = :uid")
    Optional<Alumno> findPortalByUsuarioId(@Param("uid") Long uid);

    /**
     * Detalle para administración: todas las inscripciones con programa y periodos.
     */
    @EntityGraph(Alumno.GRAPH_PROGRAMAS_INSCRIPCION)
    @Query("SELECT a FROM Alumno a WHERE a.id = :id")
    Optional<Alumno> findByIdConProgramasInscripcion(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cohortes", "programasAsignados"})
    List<Alumno> findByCohortes_IdOrderByApellidoPaternoAsc(Long cohorteId);

    long countByCohortes_Id(Long cohorteId);

    @Query("SELECT c.id, COUNT(a) FROM Alumno a JOIN a.cohortes c GROUP BY c.id")
    List<Object[]> countAlumnosGroupedByCohorteId();

    /** Sin DISTINCT: en PostgreSQL DISTINCT + ORDER BY por expresión falla (42P10). Hibernate deduplica por id. */
    @Query("SELECT a FROM Alumno a LEFT JOIN FETCH a.cohortes "
            + "ORDER BY LOWER(COALESCE(a.apellidoPaterno,'')), LOWER(COALESCE(a.apellidoMaterno,'')), LOWER(COALESCE(a.nombre,''))")
    List<Alumno> findAllWithProgramaYCohorteOrdered();

    /**
     * IDs de alumnos inscritos en el programa, ordenados (sin JOIN de colecciones → evita DISTINCT + ORDER BY en PostgreSQL).
     */
    @Query("SELECT a.id FROM Alumno a JOIN a.programasAsignados ap WHERE ap.programa.id = :programaId "
            + "ORDER BY LOWER(COALESCE(a.apellidoPaterno,'')), LOWER(COALESCE(a.apellidoMaterno,'')), LOWER(COALESCE(a.nombre,''))")
    List<Long> findIdsByProgramaIdOrderByNombreAsc(@Param("programaId") Long programaId);

    /**
     * Alumnos del programa con cohortes e inscripciones cargadas, en el mismo orden que {@link #findIdsByProgramaIdOrderByNombreAsc}.
     */
    default List<Alumno> findByProgramaIdOrderByApellidoPaternoAsc(Long programaId) {
        List<Long> ids = findIdsByProgramaIdOrderByNombreAsc(programaId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Alumno> loaded = findAllByIdInWithProgramasYCohortes(ids);
        Map<Long, Alumno> byId = loaded.stream().collect(Collectors.toMap(Alumno::getId, Function.identity(), (a, b) -> a));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /**
     * Carga inscripciones a programas y cohortes (batch por ids).
     */
    @EntityGraph(attributePaths = {"programasAsignados", "programasAsignados.programa", "cohortes"})
    @Query("SELECT a FROM Alumno a WHERE a.id IN :ids")
    List<Alumno> findAllByIdInWithProgramasYCohortes(@Param("ids") Collection<Long> ids);

    /**
     * Listado completo con inscripciones (alumno_programa) cargadas.
     * Usado por pantallas admin que soportan múltiple programa por alumno.
     */
    @EntityGraph(Alumno.GRAPH_PROGRAMAS_INSCRIPCION)
    @Query("SELECT a FROM Alumno a")
    List<Alumno> findAllConProgramasInscripcion();
}
