package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.CriterioEvaluacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CriterioEvaluacionRepository extends JpaRepository<CriterioEvaluacion, Long> {

    List<CriterioEvaluacion> findByMaestro_Id(Long maestroId);

    List<CriterioEvaluacion> findByMaestro_IdAndAsignatura_IdAndGrupo_Id(Long maestroId, Long asignaturaId, Long grupoId);

    List<CriterioEvaluacion> findByMaestro_IdAndGrupo_Id(Long maestroId, Long grupoId);

    List<CriterioEvaluacion> findByGrupo_Id(Long grupoId);

    List<CriterioEvaluacion> findByGrupo_IdAndAsignatura_Id(Long grupoId, Long asignaturaId);
}
