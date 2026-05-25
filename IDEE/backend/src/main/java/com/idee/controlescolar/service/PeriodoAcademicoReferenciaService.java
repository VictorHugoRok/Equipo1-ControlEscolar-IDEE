package com.idee.controlescolar.service;

import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.AlumnoProgramaRepository;
import com.idee.controlescolar.repository.CalificacionRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import com.idee.controlescolar.repository.HorarioBloqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * Pone en null las FK hacia {@code periodo_academico} antes de borrar periodos (evita violaciones de integridad).
 */
@Service
@RequiredArgsConstructor
public class PeriodoAcademicoReferenciaService {

    private final AlumnoRepository alumnoRepository;
    private final AlumnoProgramaRepository alumnoProgramaRepository;
    private final CalificacionRepository calificacionRepository;
    private final GrupoRepository grupoRepository;
    private final HorarioBloqueRepository horarioBloqueRepository;

    @Transactional
    public void liberarReferenciasHaciaPeriodos(Collection<Long> periodoIds) {
        if (periodoIds == null || periodoIds.isEmpty()) {
            return;
        }
        alumnoProgramaRepository.clearPeriodoIngresoByPeriodoIds(periodoIds);
        alumnoProgramaRepository.clearPeriodoActualByPeriodoIds(periodoIds);
        calificacionRepository.clearPeriodoByPeriodoIds(periodoIds);
        grupoRepository.clearPeriodoByPeriodoIds(periodoIds);
        horarioBloqueRepository.clearPeriodoByPeriodoIds(periodoIds);
    }
}
