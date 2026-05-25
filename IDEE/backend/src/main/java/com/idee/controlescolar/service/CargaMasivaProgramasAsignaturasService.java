package com.idee.controlescolar.service;

import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.AsignaturaRepository;
import com.idee.controlescolar.repository.PeriodoRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio para carga masiva de programas educativos y asignaturas desde Excel.
 * Solo usa identificadores de negocio (idPrograma, clave, idAsignatura); NO IDs internos.
 * - Hoja Programas: idPrograma, Clave, Clave DGP, Nombre, Tipo, No. periodos, Tipo periodo, Modalidad, Créditos totales, RVOE, Fecha RVOE, Estatus
 * - Hoja Asignaturas: idPrograma, Clave Programa, idAsignatura, Clave, Nombre asignatura, Tipo, No. periodo, Créditos, Horas aula, Horas práctica, Horas independientes, Estatus
 * Las asignaturas se vinculan al programa mediante idPrograma o Clave Programa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CargaMasivaProgramasAsignaturasService {

    private final ProgramaEducativoRepository programaRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final PeriodoRepository periodoRepository;
    private final PeriodoService periodoService;
    private final PlatformTransactionManager transactionManager;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static class TxOutcome<T> {
        final T entity;
        final boolean wasNew;
        TxOutcome(T entity, boolean wasNew) {
            this.entity = entity;
            this.wasNew = wasNew;
        }
    }

    public Map<String, Object> procesarExcel(MultipartFile archivo) {
        Map<String, Object> resultado = new HashMap<>();
        int programasCreados = 0;
        int programasActualizados = 0;
        int asignaturasCreadas = 0;
        int asignaturasActualizadas = 0;
        List<String> errores = new ArrayList<>();
        // Para evitar spam: agrupar asignaturas cuyo programa no se pudo resolver (por ejemplo porque el programa falló al crearse).
        Map<String, Integer> asignaturasSinProgramaCount = new LinkedHashMap<>();

        try (InputStream is = archivo.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            // Importante: NO usar una sola transacción para todo el Excel.
            // Si alguna fila falla (constraint, validación, etc.), la transacción se marca rollback-only
            // y al final ocurre: "Transaction silently rolled back because it has been marked as rollback-only".
            // Solución: transacción NUEVA por fila (así unas filas pueden guardarse aunque otras fallen).
            final TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            // Mapas por identificadores de negocio (clave, idPrograma) para vincular asignaturas
            Map<String, ProgramaEducativo> programasPorClave = new HashMap<>();
            Map<String, ProgramaEducativo> programasPorIdPrograma = new HashMap<>();

            // 1. Procesar hoja Programas (columnas: idPrograma=0, Clave=1, Clave DGP=2, Nombre=3, ...)
            Sheet sheetProgramas = wb.getSheet("Programas");
            if (sheetProgramas != null) {
                int filaInicio = 1; // Saltar encabezado
                for (int i = filaInicio; i <= sheetProgramas.getLastRowNum(); i++) {
                    Row row = sheetProgramas.getRow(i);
                    if (row == null) continue;

                    String clave = getCellString(row, 1);
                    if (clave == null || clave.isBlank() || "—".equals(clave.trim())) continue;

                    try {
                        TxOutcome<ProgramaEducativo> out = tx.execute(status -> {
                            ProgramaEducativo prog = parsePrograma(row);
                            if (prog == null) return null;

                            String claveTrim = clave.trim();
                            String idProgVal = prog.getIdPrograma() != null ? prog.getIdPrograma().trim() : null;
                            if (idProgVal != null && !idProgVal.isBlank()) {
                                if (programasPorIdPrograma.containsKey(idProgVal)) {
                                    throw new IllegalArgumentException("El ID Programa '" + idProgVal + "' ya está duplicado en este archivo.");
                                }
                            }

                            Optional<ProgramaEducativo> existente = programaRepository.findByClave(claveTrim);
                            if (existente.isPresent()) {
                                ProgramaEducativo p = existente.get();
                                actualizarProgramaDesdeFila(p, row);
                                if (idProgVal != null && !idProgVal.isBlank()
                                        && programaRepository.existsByIdProgramaAndIdNot(idProgVal, p.getId())) {
                                    throw new IllegalArgumentException("Ya existe otro programa con el ID Programa '" + idProgVal + "'.");
                                }
                                ProgramaEducativo saved = programaRepository.save(p);
                                periodoService.asegurarPeriodosParaPrograma(saved);
                                return new TxOutcome<>(saved, false);
                            }

                            if (idProgVal != null && !idProgVal.isBlank()
                                    && programaRepository.existsByIdPrograma(idProgVal)) {
                                throw new IllegalArgumentException("Ya existe un programa con el ID Programa '" + idProgVal + "'.");
                            }
                            prog.setClave(claveTrim);
                            ProgramaEducativo saved = programaRepository.save(prog);
                            periodoService.asegurarPeriodosParaPrograma(saved);
                            return new TxOutcome<>(saved, true);
                        });

                        if (out == null || out.entity == null) continue;
                        if (out.wasNew) programasCreados++;
                        else programasActualizados++;
                        ProgramaEducativo guardado = out.entity;
                        programasPorClave.put(guardado.getClave(), guardado);
                        String idProgVal = guardado.getIdPrograma() != null ? guardado.getIdPrograma().trim() : null;
                        if (idProgVal != null && !idProgVal.isBlank()) {
                            programasPorIdPrograma.put(idProgVal, guardado);
                        }
                    } catch (Exception e) {
                        String raw = e.getMessage() != null ? e.getMessage() : "Error desconocido";
                        // DataIntegrityViolationException suele venir con demasiado SQL; simplificar
                        if (e instanceof DataIntegrityViolationException) {
                            raw = "No se pudo guardar por una restricción de la base de datos.";
                        } else {
                            // Si viene un error muy largo (por ejemplo de SQL), recortarlo y humanizarlo
                            raw = humanizarError("Programa", raw);
                        }
                        errores.add("Programa fila " + (i + 1) + " (" + clave + "): " + raw);
                        log.warn("Error procesando programa fila {}: {}", i + 1, raw);
                    }
                }
            }

            // 2. Procesar hoja Asignaturas (columnas: idPrograma=0, Clave Programa=1, idAsignatura=2, Clave=3, ...)
            // Vincular a programa SOLO por idPrograma o Clave Programa (identificadores de negocio)
            Sheet sheetAsignaturas = wb.getSheet("Asignaturas");
            if (sheetAsignaturas != null) {
                int filaInicio = 1;
                for (int i = filaInicio; i <= sheetAsignaturas.getLastRowNum(); i++) {
                    Row row = sheetAsignaturas.getRow(i);
                    if (row == null) continue;

                    String claveAsig = getCellString(row, 3);
                    if (claveAsig == null || claveAsig.isBlank() || "—".equals(claveAsig.trim())) continue;

                    try {
                        final String claveAsigTrim = claveAsig.trim();
                        TxOutcome<Asignatura> out = tx.execute(status -> {
                            ProgramaEducativo programa = null;
                            String idProg = getCellString(row, 0);
                            String claveProg = getCellString(row, 1);
                            if (claveProg != null && !claveProg.isBlank() && !"—".equals(claveProg.trim())) {
                                programa = programasPorClave.get(claveProg.trim());
                                if (programa == null) {
                                    programa = programaRepository.findByClave(claveProg.trim()).orElse(null);
                                }
                            }
                            if (programa == null && idProg != null && !idProg.isBlank()) {
                                programa = programasPorIdPrograma.get(idProg.trim());
                                if (programa == null) {
                                    programa = programaRepository.findByIdPrograma(idProg.trim()).orElse(null);
                                }
                            }
                            if (programa == null) {
                                throw new IllegalArgumentException("PROGRAMA_NO_ENCONTRADO|idPrograma=" + (idProg != null ? idProg : "")
                                        + "|clavePrograma=" + (claveProg != null ? claveProg : ""));
                            }

                            Integer numeroPeriodo = parseNumeroPeriodo(getCellString(row, 6));
                            if (numeroPeriodo == null || numeroPeriodo < 1) {
                                throw new IllegalArgumentException("No. periodo inválido");
                            }

                            Periodo periodo = periodoRepository.findByProgramaIdAndNumero(programa.getId(), numeroPeriodo).orElse(null);
                            if (periodo == null) {
                                if (programa.getDuracionPeriodos() == null || programa.getDuracionPeriodos() < numeroPeriodo) {
                                    programa.setDuracionPeriodos(numeroPeriodo);
                                    programaRepository.save(programa);
                                }
                                periodoService.asegurarPeriodosParaPrograma(programa);
                                periodo = periodoRepository.findByProgramaIdAndNumero(programa.getId(), numeroPeriodo).orElse(null);
                            }
                            if (periodo == null) {
                                throw new IllegalArgumentException("Periodo " + numeroPeriodo + " no existe en programa " + programa.getClave());
                            }

                            // Buscar asignatura existente por Clave dentro del programa (identificador de negocio)
                            Asignatura asignatura = asignaturaRepository.findByProgramaIdAndClave(programa.getId(), claveAsigTrim).orElse(null);
                            boolean esNueva = (asignatura == null);
                            if (asignatura == null) asignatura = new Asignatura();

                            asignatura.setIdAsignatura(getCellString(row, 2));
                            asignatura.setClave(claveAsigTrim);
                            asignatura.setNombre(parseString(getCellString(row, 4), "Asignatura"));
                            asignatura.setTipo(parseTipoAsignatura(getCellString(row, 5)));
                            asignatura.setPeriodo(periodo);
                            asignatura.setPrograma(programa);
                            asignatura.setCreditos(parseInt(getCellString(row, 7)));
                            asignatura.setHorasAula(parseInt(getCellString(row, 8)));
                            asignatura.setHorasPractica(parseInt(getCellString(row, 9)));
                            asignatura.setHorasIndependientes(parseInt(getCellString(row, 10)));
                            asignatura.setEstatus(parseEstatusAsignatura(getCellString(row, 11)));

                            Asignatura saved = asignaturaRepository.save(asignatura);
                            return new TxOutcome<>(saved, esNueva);
                        });
                        if (out != null && out.entity != null) {
                            if (out.wasNew) asignaturasCreadas++;
                            else asignaturasActualizadas++;
                        }

                    } catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : "Error desconocido";
                        if (msg.startsWith("PROGRAMA_NO_ENCONTRADO|")) {
                            String key = msg.substring("PROGRAMA_NO_ENCONTRADO|".length());
                            // key: idPrograma=...|clavePrograma=...
                            asignaturasSinProgramaCount.put(key, asignaturasSinProgramaCount.getOrDefault(key, 0) + 1);
                        } else {
                            errores.add("Asignatura fila " + (i + 1) + " (" + claveAsig + "): " + humanizarError("Asignatura", msg));
                        }
                        log.warn("Error procesando asignatura fila {}: {}", i + 1, msg);
                    }
                }
            }

            // Emitir errores agrupados por programa faltante (mensaje más corto y claro)
            for (var entry : asignaturasSinProgramaCount.entrySet()) {
                String key = entry.getKey();
                int count = entry.getValue() != null ? entry.getValue() : 0;
                String idP = extraerValorKey(key, "idPrograma");
                String claveP = extraerValorKey(key, "clavePrograma");
                String etiqueta = (claveP != null && !claveP.isBlank()) ? ("Clave Programa=" + claveP) : ("idPrograma=" + idP);
                errores.add("Asignaturas: " + count + " fila(s) omitidas porque no se encontró el programa (" + etiqueta + "). Revisa la hoja Programas.");
            }

            resultado.put("programasCreados", programasCreados);
            resultado.put("programasActualizados", programasActualizados);
            resultado.put("asignaturasCreadas", asignaturasCreadas);
            resultado.put("asignaturasActualizadas", asignaturasActualizadas);
            resultado.put("errores", errores);
            resultado.put("exito", errores.isEmpty() || (programasCreados + programasActualizados + asignaturasCreadas + asignaturasActualizadas) > 0);

        } catch (Exception e) {
            log.error("Error al procesar Excel: {}", e.getMessage(), e);
            resultado.put("exito", false);
            resultado.put("errores", List.of("Error general: " + e.getMessage()));
        }

        return resultado;
    }

    private String humanizarError(String entidad, String raw) {
        String msg = raw != null ? raw : "";
        String low = msg.toLowerCase();
        // CHECK tipo_periodo viejo: no incluye TETRAMESTRE
        if (low.contains("programas_educativos_tipo_periodo_check") || low.contains("tipo_periodo_check")) {
            return "Tipo de periodo no permitido por la base de datos. Reinicia el backend para aplicar la corrección de esquema y reintenta.";
        }
        if (msg.contains("violates") && (low.contains("check") || low.contains("restricción") || low.contains("constraint"))) {
            return "Hay un valor inválido que no cumple una restricción de la base de datos.";
        }
        if (raw != null && raw.length() > 180) {
            return raw.substring(0, 180) + "…";
        }
        return raw;
    }

    private String extraerValorKey(String key, String name) {
        if (key == null || name == null) return null;
        // key: idPrograma=4|clavePrograma=444444
        String[] parts = key.split("\\|");
        for (String p : parts) {
            if (p == null) continue;
            String prefix = name + "=";
            if (p.startsWith(prefix)) return p.substring(prefix.length());
        }
        return null;
    }

    private ProgramaEducativo parsePrograma(Row row) {
        String nombre = parseString(getCellString(row, 3), null);
        if (nombre == null || nombre.isBlank()) return null;

        ProgramaEducativo p = new ProgramaEducativo();
        p.setIdPrograma(getCellString(row, 0));
        p.setClave(getCellString(row, 1));
        p.setClaveDgp(getCellString(row, 2));
        p.setNombre(nombre);
        p.setTipoPrograma(parseTipoPrograma(getCellString(row, 4)));
        Integer duracion = parseInt(getCellString(row, 5));
        p.setDuracionPeriodos(duracion != null && duracion > 0 ? duracion : 8);
        p.setTipoPeriodo(parseTipoPeriodo(getCellString(row, 6)));
        p.setModalidad(parseModalidad(getCellString(row, 7)));
        p.setCreditosTotales(parseInt(getCellString(row, 8)));
        if (p.getCreditosTotales() == null || p.getCreditosTotales() <= 0) p.setCreditosTotales(1);
        p.setRvoe(getCellString(row, 9));
        p.setFechaRvoe(parseFecha(getCellString(row, 10)));
        p.setEstatus(parseEstatusPrograma(getCellString(row, 11)));
        return p;
    }

    private void actualizarProgramaDesdeFila(ProgramaEducativo p, Row row) {
        String v;
        if ((v = getCellString(row, 0)) != null) p.setIdPrograma(v);
        if ((v = getCellString(row, 2)) != null) p.setClaveDgp(v);
        if ((v = getCellString(row, 3)) != null && !v.isBlank()) p.setNombre(v);
        if (parseTipoPrograma(getCellString(row, 4)) != null) p.setTipoPrograma(parseTipoPrograma(getCellString(row, 4)));
        Integer duracion = parseInt(getCellString(row, 5));
        if (duracion != null) p.setDuracionPeriodos(duracion);
        if (parseTipoPeriodo(getCellString(row, 6)) != null) p.setTipoPeriodo(parseTipoPeriodo(getCellString(row, 6)));
        if (parseModalidad(getCellString(row, 7)) != null) p.setModalidad(parseModalidad(getCellString(row, 7)));
        Integer creditos = parseInt(getCellString(row, 8));
        if (creditos != null && creditos > 0) p.setCreditosTotales(creditos);
        if ((v = getCellString(row, 9)) != null) p.setRvoe(v);
        LocalDate fecha = parseFecha(getCellString(row, 10));
        if (fecha != null) p.setFechaRvoe(fecha);
        if (parseEstatusPrograma(getCellString(row, 11)) != null) p.setEstatus(parseEstatusPrograma(getCellString(row, 11)));
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATTER)
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    double num = cell.getNumericCellValue();
                    yield String.valueOf((long) num);
                } catch (Exception e) {
                    yield cell.toString();
                }
            }
            default -> null;
        };
    }

    private String parseString(String s, String def) {
        if (s == null || s.isBlank() || "—".equals(s.trim())) return def;
        return s.trim();
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank() || "—".equals(s.trim())) return null;
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseNumeroPeriodo(String s) {
        if (s == null || s.isBlank() || "—".equals(s.trim())) return null;
        String num = s.replaceAll("[^0-9]", "");
        if (num.isEmpty()) return null;
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseFecha(String s) {
        if (s == null || s.isBlank() || "—".equals(s.trim())) return null;
        try {
            return LocalDate.parse(s.trim().substring(0, Math.min(10, s.trim().length())), DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private ProgramaEducativo.TipoPrograma parseTipoPrograma(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toUpperCase();
        return switch (v) {
            case "LICENCIATURA" -> ProgramaEducativo.TipoPrograma.LICENCIATURA;
            case "MAESTRÍA", "MAESTRIA" -> ProgramaEducativo.TipoPrograma.MAESTRIA;
            case "PROFESIONAL ASOCIADO" -> ProgramaEducativo.TipoPrograma.PROFESIONAL_ASOCIADO;
            case "TÉCNICO SUPERIOR", "TECNICO SUPERIOR" -> ProgramaEducativo.TipoPrograma.TECNICO_SUPERIOR;
            case "ESPECIALIDAD" -> ProgramaEducativo.TipoPrograma.ESPECIALIDAD;
            case "DOCTORADO" -> ProgramaEducativo.TipoPrograma.DOCTORADO;
            case "EXTRACURRICULAR" -> ProgramaEducativo.TipoPrograma.EXTRACURRICULAR;
            default -> null;
        };
    }

    private ProgramaEducativo.TipoPeriodo parseTipoPeriodo(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toUpperCase();
        if (v.contains("SEMESTRE")) return ProgramaEducativo.TipoPeriodo.SEMESTRE;
        if (v.contains("CUATRIMESTRE")) return ProgramaEducativo.TipoPeriodo.CUATRIMESTRE;
        if (v.contains("TETRAMESTRE")) return ProgramaEducativo.TipoPeriodo.TETRAMESTRE;
        if (v.contains("TRIMESTRE")) return ProgramaEducativo.TipoPeriodo.TRIMESTRE;
        return null;
    }

    private ProgramaEducativo.Modalidad parseModalidad(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toUpperCase();
        return switch (v) {
            case "ESCOLARIZADO" -> ProgramaEducativo.Modalidad.ESCOLARIZADO;
            case "MIXTO" -> ProgramaEducativo.Modalidad.MIXTO;
            case "EN LÍNEA", "EN LINEA" -> ProgramaEducativo.Modalidad.EN_LINEA;
            default -> null;
        };
    }

    private ProgramaEducativo.EstatusPrograma parseEstatusPrograma(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().equalsIgnoreCase("Inactivo") ? ProgramaEducativo.EstatusPrograma.INACTIVO : ProgramaEducativo.EstatusPrograma.ACTIVO;
    }

    private Asignatura.TipoAsignatura parseTipoAsignatura(String s) {
        if (s == null || s.isBlank()) return Asignatura.TipoAsignatura.OBLIGATORIA;
        String v = s.trim().toUpperCase();
        return switch (v) {
            case "OPTATIVA" -> Asignatura.TipoAsignatura.OPTATIVA;
            case "LIBRE" -> Asignatura.TipoAsignatura.LIBRE;
            case "EXTRACURRICULAR" -> Asignatura.TipoAsignatura.EXTRACURRICULAR;
            case "SERVICIO SOCIAL" -> Asignatura.TipoAsignatura.SERVICIO_SOCIAL;
            case "RESIDENCIA PROFESIONAL" -> Asignatura.TipoAsignatura.RESIDENCIA_PROFESIONAL;
            default -> Asignatura.TipoAsignatura.OBLIGATORIA;
        };
    }

    private Asignatura.EstatusAsignatura parseEstatusAsignatura(String s) {
        if (s == null || s.isBlank()) return Asignatura.EstatusAsignatura.ACTIVA;
        return s.trim().equalsIgnoreCase("Inactiva") ? Asignatura.EstatusAsignatura.INACTIVA : Asignatura.EstatusAsignatura.ACTIVA;
    }
}
