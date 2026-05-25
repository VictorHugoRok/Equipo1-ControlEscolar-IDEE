package com.idee.controlescolar.service;

import com.idee.controlescolar.model.CicloEscolar;
import com.idee.controlescolar.model.EstadoGestionPeriodoAcademico;
import com.idee.controlescolar.model.PeriodoAcademico;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.CicloEscolarRepository;
import com.idee.controlescolar.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Periodos académicos del catálogo institucional: se generan al crear un {@link CicloEscolar}
 * (reglas de fechas fijas por tipo). La gestión manual de altas/edición/borrado está deshabilitada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PeriodoAcademicoService {

    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final CicloEscolarRepository cicloEscolarRepository;
    private final PeriodoAcademicoReferenciaService periodoAcademicoReferenciaService;

    /** Planes {@code SEMANAL} comparten el mismo calendario y filas de catálogo que {@code SEMESTRE}. */
    public static ProgramaEducativo.TipoPeriodo tipoCatalogo(ProgramaEducativo.TipoPeriodo tipo) {
        if (tipo == ProgramaEducativo.TipoPeriodo.SEMANAL) {
            return ProgramaEducativo.TipoPeriodo.SEMESTRE;
        }
        return tipo;
    }

    public List<PeriodoAcademico> listarTodos() {
        return periodoAcademicoRepository.findAllByOrderByAnioDescNumeroDesc();
    }

    public List<PeriodoAcademico> listarPorCiclo(Long cicloId) {
        return periodoAcademicoRepository.findByCiclo_IdOrderByFechaInicioAsc(cicloId);
    }

    @Transactional(readOnly = true)
    public List<PeriodoAcademico> listarDisponibles() {
        return listarDisponiblesPorTipo(ProgramaEducativo.TipoPeriodo.SEMESTRE);
    }

    @Transactional(readOnly = true)
    public List<PeriodoAcademico> listarDisponiblesPorTipo(ProgramaEducativo.TipoPeriodo tipo) {
        ProgramaEducativo.TipoPeriodo t = tipoCatalogo((tipo != null) ? tipo : ProgramaEducativo.TipoPeriodo.SEMESTRE);
        return periodoAcademicoRepository.findByTipoPeriodoOrderByFechaInicioDesc(t);
    }

    public Optional<PeriodoAcademico> findByCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        List<PeriodoAcademico> lista = periodoAcademicoRepository.findByCodigoOrderByTipoPeriodoAsc(codigo.trim());
        if (lista == null || lista.isEmpty()) return Optional.empty();
        return Optional.of(lista.get(0));
    }

    public Optional<PeriodoAcademico> findByTipoYCodigo(ProgramaEducativo.TipoPeriodo tipo, String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        ProgramaEducativo.TipoPeriodo t = tipoCatalogo((tipo != null) ? tipo : ProgramaEducativo.TipoPeriodo.SEMESTRE);
        List<PeriodoAcademico> lista = periodoAcademicoRepository
                .findAllByTipoPeriodoAndCodigoOrderByFechaInicioDesc(t, codigo.trim());
        if (lista == null || lista.isEmpty()) return Optional.empty();
        return lista.stream()
                .filter(p -> p.getEstadoGestion() == EstadoGestionPeriodoAcademico.ACTIVO)
                .findFirst()
                .or(() -> Optional.of(lista.get(0)));
    }

    public Optional<PeriodoAcademico> findById(Long id) {
        return periodoAcademicoRepository.findById(id);
    }

    @Transactional
    public PeriodoAcademico asegurarPeriodo(String codigo) {
        return asegurarPeriodo(codigo, ProgramaEducativo.TipoPeriodo.SEMESTRE);
    }

    @Transactional
    public PeriodoAcademico asegurarPeriodo(String codigo, ProgramaEducativo.TipoPeriodo tipo) {
        if (codigo == null || codigo.isBlank()) return null;
        return findByTipoYCodigo(tipo, codigo).orElseThrow(() -> new IllegalArgumentException(
                "No existe el periodo académico «" + codigo.trim() + "» para el tipo "
                        + (tipo != null ? tipo.name() : "SEMESTRE")
                        + ". Debe existir un ciclo escolar con periodos generados para ese año."));
    }

    @Transactional
    public PeriodoAcademico asegurarPeriodoOpcional(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        return findByTipoYCodigo(ProgramaEducativo.TipoPeriodo.SEMESTRE, codigo).orElse(null);
    }

    /**
     * Genera el catálogo completo (semestre, cuatrimestre, tetramestre y trimestre) para un ciclo recién creado.
     * Fechas institucionales fijas según el año base del ciclo (año del 1 de enero de la vigencia normalizada).
     */
    @Transactional
    public void generarPeriodosCatalogoParaCiclo(Long cicloId, int anioBase) {
        CicloEscolar ciclo = cicloEscolarRepository.findById(cicloId)
                .orElseThrow(() -> new IllegalArgumentException("Ciclo escolar no encontrado."));
        if (periodoAcademicoRepository.countByCiclo_Id(cicloId) > 0) {
            log.info("generarPeriodosCatalogoParaCiclo: ciclo {} ya tiene periodos; no se regenera.", cicloId);
            return;
        }
        List<PeriodoAcademico> nuevos = new ArrayList<>();
        nuevos.addAll(construirPeriodosCuatriTetra(ciclo, anioBase));
        nuevos.addAll(construirPeriodosSemestre(ciclo, anioBase));
        nuevos.addAll(construirPeriodosTrimestre(ciclo, anioBase));
        periodoAcademicoRepository.saveAll(nuevos);
        log.info("Catálogo de periodos generado para ciclo id={} añoBase={} ({} registros)", cicloId, anioBase, nuevos.size());
    }

    private List<PeriodoAcademico> construirPeriodosCuatriTetra(CicloEscolar ciclo, int y) {
        List<PeriodoAcademico> out = new ArrayList<>();
        LocalDate[][] rangos = new LocalDate[][]{
                {firstDay(y, 1), lastDay(y, 4)},
                {firstDay(y, 5), lastDay(y, 8)},
                {firstDay(y, 9), lastDay(y, 12)}
        };
        for (int i = 0; i < rangos.length; i++) {
            int num = i + 1;
            LocalDate ini = rangos[i][0];
            LocalDate fin = rangos[i][1];
            out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.CUATRIMESTRE, num, ini, fin));
            out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.TETRAMESTRE, num, ini, fin));
        }
        return out;
    }

    private List<PeriodoAcademico> construirPeriodosSemestre(CicloEscolar ciclo, int y) {
        List<PeriodoAcademico> out = new ArrayList<>();
        out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.SEMESTRE, 1, firstDay(y, 2), lastDay(y, 7)));
        out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.SEMESTRE, 2, firstDay(y, 8), lastDay(y + 1, 1)));
        return out;
    }

    private List<PeriodoAcademico> construirPeriodosTrimestre(CicloEscolar ciclo, int y) {
        List<PeriodoAcademico> out = new ArrayList<>();
        out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.TRIMESTRE, 1, firstDay(y, 2), lastDay(y, 4)));
        out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.TRIMESTRE, 2, firstDay(y, 5), lastDay(y, 7)));
        out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.TRIMESTRE, 3, firstDay(y, 8), lastDay(y, 10)));
        out.add(fila(ciclo, ProgramaEducativo.TipoPeriodo.TRIMESTRE, 4, firstDay(y, 11), lastDay(y + 1, 1)));
        return out;
    }

    private static LocalDate firstDay(int year, int month) {
        return LocalDate.of(year, month, 1);
    }

    private static LocalDate lastDay(int year, int month) {
        return YearMonth.of(year, month).atEndOfMonth();
    }

    private PeriodoAcademico fila(CicloEscolar ciclo, ProgramaEducativo.TipoPeriodo tipo, int numero,
                                  LocalDate fechaInicio, LocalDate fechaFin) {
        String codigo = fechaInicio.getYear() + "-" + numero;
        String nombre = codigo + " · " + etiquetaTipo(tipo);
        PeriodoAcademico p = new PeriodoAcademico();
        p.setCiclo(ciclo);
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setAnio(fechaInicio.getYear());
        p.setNumero(numero);
        p.setFechaInicio(fechaInicio);
        p.setFechaFin(fechaFin);
        p.setTipoPeriodo(tipo);
        p.setEstadoGestion(EstadoGestionPeriodoAcademico.INACTIVO);
        p.setActivo(false);
        return p;
    }

    private static String etiquetaTipo(ProgramaEducativo.TipoPeriodo tipo) {
        if (tipo == null) return "Periodo";
        return switch (tipo) {
            case SEMESTRE -> "Semestre";
            case CUATRIMESTRE -> "Cuatrimestre";
            case TETRAMESTRE -> "Tetramestre";
            case TRIMESTRE -> "Trimestre";
            case SEMANAL -> "Semanal";
        };
    }

    @Transactional
    public PeriodoAcademico crearPeriodoAdministrativo(Long cicloId,
                                                         ProgramaEducativo.TipoPeriodo tipoPeriodo,
                                                         int numero,
                                                         String nombreVisible,
                                                         String codigo,
                                                         LocalDate fechaInicio,
                                                         LocalDate fechaFin) {
        throw new IllegalArgumentException(
                "El alta manual de periodos está deshabilitada. Los periodos se generan al crear el ciclo escolar.");
    }

    @Transactional
    public PeriodoAcademico actualizarPeriodoAdministrativo(Long periodoId,
                                                             Long cicloIdOpcional,
                                                             ProgramaEducativo.TipoPeriodo tipoPeriodo,
                                                             int numero,
                                                             String nombreVisible,
                                                             String codigo,
                                                             LocalDate fechaInicio,
                                                             LocalDate fechaFin) {
        throw new IllegalArgumentException(
                "La edición manual de periodos está deshabilitada. Use solo el cambio de estado de gestión.");
    }

    @Transactional
    public void eliminarPeriodoAdministrativo(Long periodoId) {
        throw new IllegalArgumentException(
                "No se pueden eliminar periodos del catálogo. Si necesita corregir un ciclo, elimine el ciclo completo (liberando referencias) y créelo de nuevo.");
    }

    @Transactional
    public void eliminarTodosLosPeriodosDelCiclo(Long cicloId) {
        List<PeriodoAcademico> lista = periodoAcademicoRepository.findByCiclo_IdOrderByFechaInicioAsc(cicloId);
        if (lista.isEmpty()) {
            return;
        }
        List<Long> ids = lista.stream().map(PeriodoAcademico::getId).toList();
        periodoAcademicoReferenciaService.liberarReferenciasHaciaPeriodos(ids);
        periodoAcademicoRepository.deleteAllById(ids);
        log.info("Periodos académicos eliminados en cascada por ciclo: cicloId={} count={}", cicloId, ids.size());
    }

    @Transactional
    public PeriodoAcademico cambiarEstadoGestion(Long periodoId, EstadoGestionPeriodoAcademico nuevoEstado) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("Estado inválido.");
        }
        PeriodoAcademico p = periodoAcademicoRepository.findById(periodoId)
                .orElseThrow(() -> new IllegalArgumentException("Periodo académico no encontrado."));
        EstadoGestionPeriodoAcademico actual = p.getEstadoGestion() != null ? p.getEstadoGestion() : EstadoGestionPeriodoAcademico.INACTIVO;

        if (nuevoEstado == EstadoGestionPeriodoAcademico.ACTIVO) {
            if (actual == EstadoGestionPeriodoAcademico.ACTIVO) {
                return p;
            }
            validarPuedeActivar(p);
            int otros = periodoAcademicoRepository.countByTipoPeriodoAndEstadoGestionAndIdNot(
                    p.getTipoPeriodo(), EstadoGestionPeriodoAcademico.ACTIVO, p.getId());
            if (otros > 0) {
                throw new IllegalArgumentException(
                        "Ya hay otro periodo académico ACTIVO para el tipo " + p.getTipoPeriodo()
                                + ". Ciérrelo o desactívelo antes de activar este.");
            }
        }

        p.setEstadoGestion(nuevoEstado);
        p.sincronizarFlagActivoLegacy();
        return periodoAcademicoRepository.save(p);
    }

    private void validarPuedeActivar(PeriodoAcademico p) {
        if (p.getNumero() == null || p.getNumero() <= 1) {
            return;
        }
        int prevNum = p.getNumero() - 1;
        CicloEscolar ciclo = p.getCiclo();
        if (ciclo == null || ciclo.getId() == null) {
            throw new IllegalArgumentException("El periodo no tiene ciclo asignado.");
        }
        PeriodoAcademico anterior = periodoAcademicoRepository
                .findByCiclo_IdAndTipoPeriodoAndNumero(ciclo.getId(), p.getTipoPeriodo(), prevNum)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el periodo anterior (número " + prevNum + ") en este ciclo y tipo."));
        if (anterior.getEstadoGestion() != EstadoGestionPeriodoAcademico.CERRADO) {
            throw new IllegalArgumentException(
                    "El periodo anterior («" + anterior.getNombre() + "») debe estar CERRADO antes de activar este.");
        }
    }

    public Optional<PeriodoAcademico> obtenerPeriodoActivoPorTipo(ProgramaEducativo.TipoPeriodo tipo) {
        ProgramaEducativo.TipoPeriodo t = tipoCatalogo((tipo != null) ? tipo : ProgramaEducativo.TipoPeriodo.SEMESTRE);
        return periodoAcademicoRepository.findFirstByTipoPeriodoAndEstadoGestionOrderByFechaInicioDesc(
                t, EstadoGestionPeriodoAcademico.ACTIVO);
    }

    public Optional<PeriodoAcademico> obtenerPeriodoActivo() {
        return periodoAcademicoRepository.findFirstByActivoTrue();
    }

    @Transactional
    public void actualizarPeriodoActivo() {
        // no-op
    }

    public static int periodosPorAnio(ProgramaEducativo.TipoPeriodo tipo) {
        if (tipo == null) {
            return 2;
        }
        return switch (tipo) {
            case SEMANAL -> 2;
            case SEMESTRE -> 2;
            case CUATRIMESTRE, TETRAMESTRE -> 3;
            case TRIMESTRE -> 4;
        };
    }

    public static Optional<int[]> parseAnioNumeroPeriodo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String s = codigo.trim();
        int dash = s.lastIndexOf('-');
        if (dash <= 0 || dash >= s.length() - 1) {
            return Optional.empty();
        }
        try {
            int anio = Integer.parseInt(s.substring(0, dash).trim());
            int num = Integer.parseInt(s.substring(dash + 1).trim());
            if (num < 1) {
                return Optional.empty();
            }
            return Optional.of(new int[]{anio, num});
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> siguientePeriodoCodigo(String codigo, ProgramaEducativo.TipoPeriodo tipo) {
        int max = periodosPorAnio(tipo);
        return parseAnioNumeroPeriodo(codigo).map(arr -> {
            int y = arr[0];
            int n = arr[1];
            if (n < max) {
                return y + "-" + (n + 1);
            }
            return (y + 1) + "-1";
        });
    }

    public static Optional<String> avanzarPeriodosDesde(String codigoInicio, ProgramaEducativo.TipoPeriodo tipo, int pasos) {
        if (codigoInicio == null || codigoInicio.isBlank()) {
            return Optional.empty();
        }
        if (pasos <= 0) {
            return Optional.of(codigoInicio.trim());
        }
        String cur = codigoInicio.trim();
        for (int i = 0; i < pasos; i++) {
            Optional<String> next = siguientePeriodoCodigo(cur, tipo);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            cur = next.get();
        }
        return Optional.of(cur);
    }

    public static Optional<String> codigoPeriodoDelNivelDelPlan(String codigoIngreso, ProgramaEducativo.TipoPeriodo tipo, int nivelPlan) {
        if (nivelPlan < 1) {
            return Optional.empty();
        }
        return avanzarPeriodosDesde(codigoIngreso, tipo, nivelPlan - 1);
    }

    public static String codigoPeriodoActual(ProgramaEducativo.TipoPeriodo tipo) {
        if (tipo == null) tipo = ProgramaEducativo.TipoPeriodo.SEMESTRE;
        tipo = tipoCatalogo(tipo);
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        return switch (tipo) {
            case SEMESTRE -> (month >= 2 && month <= 7) ? year + "-1" : (month == 1 ? (year - 1) + "-2" : year + "-2");
            case CUATRIMESTRE, TETRAMESTRE -> (month >= 1 && month <= 4)
                    ? year + "-1"
                    : (month >= 5 && month <= 8 ? year + "-2" : year + "-3");
            case TRIMESTRE -> month >= 2 && month <= 4 ? year + "-1"
                    : (month >= 5 && month <= 7 ? year + "-2"
                    : (month >= 8 && month <= 10 ? year + "-3"
                    : (month == 11 || month == 12 ? year + "-4" : (year - 1) + "-4")));
            default -> year + "-1";
        };
    }

    public String codigoPeriodoVigenteOCalculado(ProgramaEducativo.TipoPeriodo tipo) {
        return obtenerPeriodoActivoPorTipo(tipo)
                .map(PeriodoAcademico::getCodigo)
                .orElse(codigoPeriodoActual(tipo));
    }
}
