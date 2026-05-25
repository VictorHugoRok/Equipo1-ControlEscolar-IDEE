package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.EvaluacionDocenteFormulario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvaluacionDocenteFormularioRepository extends JpaRepository<EvaluacionDocenteFormulario, Long> {

    List<EvaluacionDocenteFormulario> findAllByOrderByFechaCreacionDesc();

    @EntityGraph(attributePaths = {"preguntas"})
    @Query("SELECT f FROM EvaluacionDocenteFormulario f WHERE f.id = :id")
    Optional<EvaluacionDocenteFormulario> findByIdWithPreguntas(@Param("id") Long id);

    @EntityGraph(attributePaths = {"preguntas"})
    List<EvaluacionDocenteFormulario> findByActivoTrueOrderByFechaCreacionDesc();
}
