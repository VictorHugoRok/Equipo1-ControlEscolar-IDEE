package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.EstadoGestionPeriodoAcademico;
import com.idee.controlescolar.model.PeriodoAcademico;
import com.idee.controlescolar.model.ProgramaEducativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PeriodoAcademicoRepository extends JpaRepository<PeriodoAcademico, Long> {

    List<PeriodoAcademico> findByCodigoOrderByTipoPeriodoAsc(String codigo);

    List<PeriodoAcademico> findAllByTipoPeriodoAndCodigoOrderByFechaInicioDesc(ProgramaEducativo.TipoPeriodo tipoPeriodo, String codigo);

    Optional<PeriodoAcademico> findByCiclo_IdAndTipoPeriodoAndCodigo(Long cicloId, ProgramaEducativo.TipoPeriodo tipoPeriodo, String codigo);

    Optional<PeriodoAcademico> findByCiclo_IdAndTipoPeriodoAndNumero(Long cicloId, ProgramaEducativo.TipoPeriodo tipoPeriodo, Integer numero);

    List<PeriodoAcademico> findAllByOrderByAnioDescNumeroDesc();

    List<PeriodoAcademico> findByTipoPeriodoOrderByFechaInicioDesc(ProgramaEducativo.TipoPeriodo tipoPeriodo);

    List<PeriodoAcademico> findByCiclo_IdOrderByFechaInicioAsc(Long cicloId);

    List<PeriodoAcademico> findByActivoTrue();

    Optional<PeriodoAcademico> findFirstByActivoTrue();

    Optional<PeriodoAcademico> findFirstByTipoPeriodoAndEstadoGestionOrderByFechaInicioDesc(
            ProgramaEducativo.TipoPeriodo tipoPeriodo, EstadoGestionPeriodoAcademico estadoGestion);

    int countByTipoPeriodoAndEstadoGestion(ProgramaEducativo.TipoPeriodo tipoPeriodo, EstadoGestionPeriodoAcademico estadoGestion);

    int countByTipoPeriodoAndEstadoGestionAndIdNot(
            ProgramaEducativo.TipoPeriodo tipoPeriodo, EstadoGestionPeriodoAcademico estadoGestion, Long id);

    boolean existsByTipoPeriodoAndCodigo(ProgramaEducativo.TipoPeriodo tipoPeriodo, String codigo);

    boolean existsByCiclo_IdAndTipoPeriodoAndNumero(Long cicloId, ProgramaEducativo.TipoPeriodo tipoPeriodo, Integer numero);

    List<PeriodoAcademico> findByAnioBetweenOrderByAnioDescNumeroDesc(int anioDesde, int anioHasta);

    List<PeriodoAcademico> findByTipoPeriodoAndAnioBetweenOrderByAnioDescNumeroDesc(ProgramaEducativo.TipoPeriodo tipoPeriodo, int anioDesde, int anioHasta);

    Optional<PeriodoAcademico> findFirstByFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(LocalDate fecha, LocalDate fecha2);

    long countByCiclo_Id(Long cicloId);

    boolean existsByCiclo_IdAndTipoPeriodoAndNumeroAndIdNot(Long cicloId, ProgramaEducativo.TipoPeriodo tipoPeriodo, Integer numero, Long id);

    boolean existsByCiclo_IdAndTipoPeriodoAndCodigoAndIdNot(Long cicloId, ProgramaEducativo.TipoPeriodo tipoPeriodo, String codigo, Long id);

    /**
     * Periodos del mismo ciclo y tipo cuyo rango de fechas intersecta [ini, fin] (inclusive), excluyendo un id si se indica.
     */
    @Query("SELECT p FROM PeriodoAcademico p WHERE p.ciclo.id = :cicloId AND p.tipoPeriodo = :tipoPeriodo "
            + "AND (:excludeId IS NULL OR p.id <> :excludeId) "
            + "AND NOT (p.fechaFin < :ini OR p.fechaInicio > :fin)")
    List<PeriodoAcademico> findSolapandoEnCiclo(
            @Param("cicloId") Long cicloId,
            @Param("tipoPeriodo") ProgramaEducativo.TipoPeriodo tipoPeriodo,
            @Param("ini") LocalDate ini,
            @Param("fin") LocalDate fin,
            @Param("excludeId") Long excludeId);
}
