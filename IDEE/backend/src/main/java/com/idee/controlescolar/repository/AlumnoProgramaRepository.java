package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.AlumnoPrograma;
import com.idee.controlescolar.model.AlumnoProgramaId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlumnoProgramaRepository extends JpaRepository<AlumnoPrograma, AlumnoProgramaId> {

    @EntityGraph(attributePaths = {"alumno", "programa", "periodoIngreso", "periodoAcademicoActual"})
    List<AlumnoPrograma> findByPrograma_IdAndEstatusMatriculaOrderByAlumno_ApellidoPaternoAsc(
            Long programaId, AlumnoPrograma.EstatusMatriculaPrograma estatus);

    @EntityGraph(attributePaths = {"programa", "periodoIngreso", "periodoAcademicoActual"})
    List<AlumnoPrograma> findByAlumno_Id(Long alumnoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AlumnoPrograma ap WHERE ap.alumno.id = :alumnoId")
    int deleteByAlumnoId(@Param("alumnoId") Long alumnoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AlumnoPrograma ap WHERE ap.alumno.id = :alumnoId AND ap.programa.id NOT IN :programaIds")
    int deleteByAlumnoIdAndProgramaIdNotIn(@Param("alumnoId") Long alumnoId, @Param("programaIds") Collection<Long> programaIds);

    Optional<AlumnoPrograma> findByAlumno_IdAndPrograma_Id(Long alumnoId, Long programaId);

    boolean existsByAlumno_IdAndPrograma_Id(Long alumnoId, Long programaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AlumnoPrograma ap SET ap.periodoIngreso = null WHERE ap.periodoIngreso.id IN :ids")
    void clearPeriodoIngresoByPeriodoIds(@Param("ids") Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AlumnoPrograma ap SET ap.periodoAcademicoActual = null WHERE ap.periodoAcademicoActual.id IN :ids")
    void clearPeriodoActualByPeriodoIds(@Param("ids") Collection<Long> ids);
}

