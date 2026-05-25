package com.idee.controlescolar.service;

import com.idee.controlescolar.model.Periodo;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.PeriodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para gestionar los periodos del plan de estudios.
 * Los periodos son fijos: al crear o actualizar un programa con duracionPeriodos,
 * se crean automáticamente los periodos 1..N.
 */
@Service
public class PeriodoService {

    private static final Logger logger = LoggerFactory.getLogger(PeriodoService.class);

    private final PeriodoRepository periodoRepository;

    public PeriodoService(PeriodoRepository periodoRepository) {
        this.periodoRepository = periodoRepository;
    }

    /**
     * Asegura que el programa tenga exactamente duracionPeriodos periodos (1..N).
     * Crea los que falten. No elimina periodos existentes que tengan asignaturas.
     */
    @Transactional
    public List<Periodo> asegurarPeriodosParaPrograma(ProgramaEducativo programa) {
        if (programa == null || programa.getDuracionPeriodos() == null || programa.getDuracionPeriodos() < 1) {
            return List.of();
        }
        int n = programa.getDuracionPeriodos();
        List<Periodo> existentes = periodoRepository.findByProgramaIdOrderByNumeroAsc(programa.getId());
        List<Periodo> resultado = new ArrayList<>();

        for (int i = 1; i <= n; i++) {
            final int num = i;
            Periodo p = existentes.stream()
                    .filter(per -> per.getNumero() == num)
                    .findFirst()
                    .orElseGet(() -> {
                        Periodo nuevo = new Periodo();
                        nuevo.setPrograma(programa);
                        nuevo.setNumero(num);
                        nuevo.setNombre(nombrePorDefecto(programa, num));
                        return periodoRepository.save(nuevo);
                    });
            resultado.add(p);
        }
        return resultado;
    }

    private String nombrePorDefecto(ProgramaEducativo programa, int numero) {
        if (programa.getTipoPeriodo() == null) return "Periodo " + numero;
        return switch (programa.getTipoPeriodo()) {
            case SEMANAL -> numero + "° Periodo";
            case SEMESTRE -> numero + "° Semestre";
            case TRIMESTRE -> numero + "° Trimestre";
            case CUATRIMESTRE -> numero + "° Cuatrimestre";
            case TETRAMESTRE -> numero + "° Tetramestre";
        };
    }
}
