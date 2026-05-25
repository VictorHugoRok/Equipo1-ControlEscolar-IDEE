package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Periodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PeriodoRepository extends JpaRepository<Periodo, Long> {

    List<Periodo> findByProgramaIdOrderByNumeroAsc(Long programaId);

    Optional<Periodo> findByProgramaIdAndNumero(Long programaId, Integer numero);

    boolean existsByProgramaIdAndNumero(Long programaId, Integer numero);
}
