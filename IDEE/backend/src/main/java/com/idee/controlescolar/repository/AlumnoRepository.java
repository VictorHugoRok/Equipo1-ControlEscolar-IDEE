package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Alumno;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para gestionar alumnos.
 */
@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

    /**
     * Busca un alumno por matrícula con programa cargado
     */
    @EntityGraph(attributePaths = {"programa"})
    Optional<Alumno> findByMatricula(String matricula);

    /**
     * Busca un alumno por CURP con programa cargado
     */
    @EntityGraph(attributePaths = {"programa"})
    Optional<Alumno> findByCurp(String curp);

    /**
     * Verifica si existe un alumno con la matrícula especificada
     */
    boolean existsByMatricula(String matricula);
}
