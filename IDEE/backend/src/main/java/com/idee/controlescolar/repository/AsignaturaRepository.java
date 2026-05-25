package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Asignatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para gestionar asignaturas
 */
@Repository
public interface AsignaturaRepository extends JpaRepository<Asignatura, Long> {

    List<Asignatura> findByProgramaId(Long programaId);

    @Query("SELECT DISTINCT a FROM Asignatura a "
            + "LEFT JOIN FETCH a.periodo "
            + "LEFT JOIN FETCH a.programa "
            + "WHERE a.programa.id = :programaId")
    List<Asignatura> findByProgramaIdWithPeriodoYPrograma(@Param("programaId") Long programaId);

    List<Asignatura> findByPeriodoId(Long periodoId);

    @Query("SELECT DISTINCT a FROM Asignatura a "
            + "LEFT JOIN FETCH a.periodo "
            + "LEFT JOIN FETCH a.programa "
            + "WHERE a.periodo.id = :periodoId")
    List<Asignatura> findByPeriodoIdWithPeriodoYPrograma(@Param("periodoId") Long periodoId);

    @Query("SELECT DISTINCT a FROM Asignatura a LEFT JOIN FETCH a.periodo LEFT JOIN FETCH a.programa")
    List<Asignatura> findAllWithPeriodoYPrograma();

    Optional<Asignatura> findByProgramaIdAndClave(Long programaId, String clave);

    /**
     * Busca una asignatura por el identificador de negocio (idAsignatura) dentro de un programa.
     */
    Optional<Asignatura> findByProgramaIdAndIdAsignatura(Long programaId, String idAsignatura);
}
