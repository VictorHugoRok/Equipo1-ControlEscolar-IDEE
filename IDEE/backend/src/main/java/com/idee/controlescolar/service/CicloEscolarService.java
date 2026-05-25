package com.idee.controlescolar.service;

import com.idee.controlescolar.model.CicloEscolar;
import com.idee.controlescolar.model.CicloEscolarEstado;
import com.idee.controlescolar.repository.CicloEscolarRepository;
import com.idee.controlescolar.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CicloEscolarService {

    private final CicloEscolarRepository cicloEscolarRepository;
    private final PeriodoAcademicoService periodoAcademicoService;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;

    public List<CicloEscolar> listarTodosOrdenados() {
        return cicloEscolarRepository.findAllByOrderByFechaInicioDesc();
    }

    public CicloEscolar obtenerObligatorio(Long id) {
        return cicloEscolarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ciclo escolar no encontrado."));
    }

    @Transactional
    public CicloEscolar crear(String nombre, LocalDate fechaInicio, LocalDate fechaFin,
                              CicloEscolarEstado estado) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del ciclo es obligatorio.");
        }
        if (fechaInicio == null || fechaFin == null || fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("Las fechas del ciclo son inválidas.");
        }
        int anioBase = fechaInicio.getYear();
        LocalDate vigenciaInicio = LocalDate.of(anioBase, 1, 1);
        LocalDate vigenciaFin = YearMonth.of(anioBase + 1, 1).atEndOfMonth();
        CicloEscolar c = new CicloEscolar();
        c.setNombre(nombre.trim());
        c.setFechaInicio(vigenciaInicio);
        c.setFechaFin(vigenciaFin);
        c.setEstado(estado != null ? estado : CicloEscolarEstado.ACTIVO);
        c = cicloEscolarRepository.save(c);
        periodoAcademicoService.generarPeriodosCatalogoParaCiclo(c.getId(), anioBase);
        return c;
    }

    @Transactional
    public CicloEscolar actualizar(Long id, String nombre, LocalDate fechaInicio,
                                   LocalDate fechaFin, CicloEscolarEstado estado) {
        CicloEscolar c = obtenerObligatorio(id);
        boolean tienePeriodos = periodoAcademicoRepository.countByCiclo_Id(id) > 0;
        if (tienePeriodos) {
            if (fechaInicio != null && !fechaInicio.equals(c.getFechaInicio())) {
                throw new IllegalArgumentException(
                        "No se puede cambiar la fecha de inicio: el ciclo ya tiene periodos académicos generados.");
            }
            if (fechaFin != null && !fechaFin.equals(c.getFechaFin())) {
                throw new IllegalArgumentException(
                        "No se puede cambiar la fecha de fin: el ciclo ya tiene periodos académicos generados.");
            }
        } else {
            if (fechaInicio != null) c.setFechaInicio(fechaInicio);
            if (fechaFin != null) c.setFechaFin(fechaFin);
            if (c.getFechaFin() != null && c.getFechaInicio() != null
                    && c.getFechaFin().isBefore(c.getFechaInicio())) {
                throw new IllegalArgumentException("La fecha fin debe ser posterior al inicio.");
            }
        }
        if (nombre != null && !nombre.isBlank()) {
            c.setNombre(nombre.trim());
        }
        if (estado != null) c.setEstado(estado);
        return cicloEscolarRepository.save(c);
    }

    @Transactional
    public void eliminar(Long id) {
        obtenerObligatorio(id);
        periodoAcademicoService.eliminarTodosLosPeriodosDelCiclo(id);
        cicloEscolarRepository.deleteById(id);
    }
}
