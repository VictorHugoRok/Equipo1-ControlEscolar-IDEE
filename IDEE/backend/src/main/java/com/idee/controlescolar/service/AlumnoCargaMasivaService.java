package com.idee.controlescolar.service;

import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio para carga masiva de alumnos desde archivo Excel.
 * El formato debe coincidir con el Excel generado por la descarga:
 * Matrícula, Nombre, Apellido paterno, Apellido materno, CURP, Género, Fecha nacimiento,
 * Programa, Periodo cursando, Ciclo escolar, Turno, Estatus matrícula, Correo institucional,
 * Correo personal, Teléfono, Código postal, Contacto emergencia, Tel. emergencia, Estado, Observaciones
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlumnoCargaMasivaService {

    private final AlumnoRepository alumnoRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PeriodoAcademicoService periodoAcademicoService;

    private static final String PASSWORD_ALUMNO = "idee1234";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, Alumno.Sexo> SEXO_MAP = Map.of(
            "MASCULINO", Alumno.Sexo.MASCULINO, "Masculino", Alumno.Sexo.MASCULINO, "M", Alumno.Sexo.MASCULINO,
            "FEMENINO", Alumno.Sexo.FEMENINO, "Femenino", Alumno.Sexo.FEMENINO, "F", Alumno.Sexo.FEMENINO
    );

    private static final Map<String, Alumno.Turno> TURNO_MAP = Map.of(
            "MATUTINO", Alumno.Turno.MATUTINO, "Matutino", Alumno.Turno.MATUTINO,
            "VESPERTINO", Alumno.Turno.VESPERTINO, "Vespertino", Alumno.Turno.VESPERTINO,
            "MIXTO", Alumno.Turno.MIXTO, "Mixto", Alumno.Turno.MIXTO
    );

    private static final Map<String, Alumno.EstatusMatricula> ESTATUS_MAP = Map.of(
            "ACTIVA", Alumno.EstatusMatricula.ACTIVA, "Activa", Alumno.EstatusMatricula.ACTIVA,
            "INACTIVA", Alumno.EstatusMatricula.BAJA_TEMPORAL, "Inactiva", Alumno.EstatusMatricula.BAJA_TEMPORAL,
            "BAJA_TEMPORAL", Alumno.EstatusMatricula.BAJA_TEMPORAL, "Baja temporal", Alumno.EstatusMatricula.BAJA_TEMPORAL,
            "BAJA_DEFINITIVA", Alumno.EstatusMatricula.BAJA_DEFINITIVA, "Baja definitiva", Alumno.EstatusMatricula.BAJA_DEFINITIVA,
            "EGRESADO", Alumno.EstatusMatricula.EGRESADO, "Egresado", Alumno.EstatusMatricula.EGRESADO
    );

    public Map<String, Object> procesarCargaMasiva(MultipartFile archivo) {
        Map<String, Object> resultado = new HashMap<>();
        List<Map<String, Object>> errores = new ArrayList<>();
        int creados = 0;
        int actualizados = 0;
        int omitidos = 0;

        try (InputStream is = archivo.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() < 2) {
                resultado.put("error", "El archivo no contiene datos o está vacío.");
                return resultado;
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                resultado.put("error", "El archivo no tiene encabezados.");
                return resultado;
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String matricula = getCellValue(row, 0);
                if (matricula == null || matricula.isBlank() || "—".equals(matricula.trim())) {
                    omitidos++;
                    continue;
                }
                matricula = matricula.trim();

                try {
                    Alumno alumno = null;
                    boolean esNuevo = false;
                    Optional<Alumno> existenteOpt = alumnoRepository.findByMatricula(matricula);

                    if (existenteOpt.isPresent()) {
                        alumno = existenteOpt.get();
                    } else {
                        alumno = new Alumno();
                        esNuevo = true;
                    }

                    String nombre = getCellValue(row, 1);
                    String apellidoPaterno = getCellValue(row, 2);
                    String apellidoMaterno = getCellValue(row, 3);
                    String curp = getCellValue(row, 4);

                    if (nombre == null || nombre.isBlank() || apellidoPaterno == null || apellidoPaterno.isBlank() ||
                            apellidoMaterno == null || apellidoMaterno.isBlank() || curp == null || curp.isBlank()) {
                        errores.add(Map.of("fila", i + 1, "matricula", matricula, "mensaje", "Faltan datos obligatorios (nombre, apellidos o CURP)"));
                        omitidos++;
                        continue;
                    }

                    curp = curp.trim().toUpperCase();
                    if (curp.length() != 18) {
                        errores.add(Map.of("fila", i + 1, "matricula", matricula, "mensaje", "CURP debe tener 18 caracteres"));
                        omitidos++;
                        continue;
                    }

                    Optional<Alumno> curpExistente = alumnoRepository.findByCurp(curp);
                    if (curpExistente.isPresent() && (alumno.getId() == null || !curpExistente.get().getId().equals(alumno.getId()))) {
                        errores.add(Map.of("fila", i + 1, "matricula", matricula, "mensaje", "El CURP ya está registrado"));
                        omitidos++;
                        continue;
                    }

                    alumno.setMatricula(matricula);
                    alumno.setNombre(nombre != null ? nombre.trim().toUpperCase(java.util.Locale.forLanguageTag("es")) : null);
                    alumno.setApellidoPaterno(apellidoPaterno != null ? apellidoPaterno.trim().toUpperCase(java.util.Locale.forLanguageTag("es")) : null);
                    alumno.setApellidoMaterno(apellidoMaterno != null ? apellidoMaterno.trim().toUpperCase(java.util.Locale.forLanguageTag("es")) : null);
                    alumno.setCurp(curp);

                    String sexoStr = getCellValue(row, 5);
                    Alumno.Sexo sexo = parseSexo(sexoStr);
                    if (sexo == null) {
                        sexo = Alumno.Sexo.MASCULINO;
                    }
                    alumno.setSexo(sexo);

                    String fechaNacStr = getCellValue(row, 6);
                    if (fechaNacStr != null && !fechaNacStr.isBlank() && !"—".equals(fechaNacStr.trim())) {
                        try {
                            if (fechaNacStr.contains("-")) {
                                alumno.setFechaNacimiento(LocalDate.parse(fechaNacStr.trim().substring(0, 10), DATE_FORMAT));
                            } else if (fechaNacStr.matches("\\d{8}")) {
                                alumno.setFechaNacimiento(LocalDate.parse(fechaNacStr, DateTimeFormatter.ofPattern("yyyyMMdd")));
                            }
                        } catch (Exception ignored) {}
                    }

                    String programaStr = getCellValue(row, 7);
                    if (programaStr != null && !programaStr.isBlank() && !"—".equals(programaStr.trim()) && !"Sin programa".equalsIgnoreCase(programaStr.trim())) {
                        Optional<ProgramaEducativo> progOpt = programaRepository.findByNombreIgnoreCase(programaStr.trim());
                        if (progOpt.isEmpty()) {
                            progOpt = programaRepository.findByClave(programaStr.trim());
                        }
                        if (progOpt.isEmpty()) {
                            List<ProgramaEducativo> progs = programaRepository.findByNombreContainingIgnoreCase(programaStr.trim());
                            if (!progs.isEmpty()) {
                                progOpt = Optional.of(progs.get(0));
                            }
                        }
                        progOpt.ifPresent(alumno::setPrograma);
                    }

                    String periodoStr = getCellValue(row, 8);
                    if (periodoStr != null && !periodoStr.isBlank() && !"—".equals(periodoStr.trim())) {
                        try {
                            alumno.setPeriodoCursando(Integer.parseInt(periodoStr.trim().replaceAll("[^0-9]", "")));
                        } catch (NumberFormatException ignored) {}
                    }

                    String cicloStr = getCellValue(row, 9);
                    if (cicloStr != null && !cicloStr.isBlank() && !"—".equals(cicloStr.trim())) {
                        ProgramaEducativo.TipoPeriodo tipo = null;
                        try {
                            if (alumno.getPrograma() != null) {
                                tipo = alumno.getPrograma().getTipoPeriodo();
                            }
                        } catch (Exception ignored) {}
                        alumno.setPeriodoAcademico(periodoAcademicoService.asegurarPeriodo(cicloStr.trim(), tipo));
                    }

                    String turnoStr = getCellValue(row, 10);
                    alumno.setTurno(parseTurno(turnoStr));

                    String estatusStr = getCellValue(row, 11);
                    Alumno.EstatusMatricula estatus = parseEstatus(estatusStr);
                    if (estatus != null) {
                        alumno.setEstatusMatricula(estatus);
                    }

                    String correoInst = getCellValue(row, 12);
                    if (correoInst != null && !correoInst.isBlank() && !"—".equals(correoInst.trim())) {
                        alumno.setCorreoInstitucional(correoInst.trim());
                    }
                    String correoPers = getCellValue(row, 13);
                    if (correoPers != null && !correoPers.isBlank() && !"—".equals(correoPers.trim())) {
                        alumno.setCorreoPersonal(correoPers.trim());
                    }
                    String telefono = getCellValue(row, 14);
                    if (telefono != null && !telefono.isBlank() && !"—".equals(telefono.trim())) {
                        alumno.setTelefono(telefono.trim());
                    }
                    String cp = getCellValue(row, 15);
                    if (cp != null && !cp.isBlank() && !"—".equals(cp.trim())) {
                        alumno.setCodigoPostal(cp.trim());
                    }
                    String contactoEmerg = getCellValue(row, 16);
                    if (contactoEmerg != null && !contactoEmerg.isBlank() && !"—".equals(contactoEmerg.trim())) {
                        alumno.setNombreContactoEmergencia(contactoEmerg.trim());
                    }
                    String telEmerg = getCellValue(row, 17);
                    if (telEmerg != null && !telEmerg.isBlank() && !"—".equals(telEmerg.trim())) {
                        alumno.setTelefonoContactoEmergencia(telEmerg.trim());
                    }
                    String estado = getCellValue(row, 18);
                    if (estado != null && !estado.isBlank() && !"—".equals(estado.trim())) {
                        alumno.setEstado(estado.trim());
                    }
                    String observaciones = getCellValue(row, 19);
                    if (observaciones != null && !observaciones.isBlank() && !"—".equals(observaciones.trim())) {
                        alumno.setObservaciones(observaciones.trim());
                    }

                    if (esNuevo) {
                        Optional<Usuario> usuarioOpt = alumno.getCorreoInstitucional() != null && !alumno.getCorreoInstitucional().isBlank()
                                ? usuarioRepository.findByEmail(alumno.getCorreoInstitucional())
                                : Optional.empty();
                        if (usuarioOpt.isEmpty()) {
                            Usuario usuario = crearUsuarioAlumno(alumno.getCorreoInstitucional());
                            alumno.setUsuario(usuario);
                        }
                        alumnoRepository.save(alumno);
                        creados++;
                        try {
                            String correoDest = alumno.getCorreoPersonal() != null && !alumno.getCorreoPersonal().isBlank()
                                    ? alumno.getCorreoPersonal() : alumno.getCorreoInstitucional();
                            if (correoDest != null && !correoDest.isBlank()) {
                                emailService.enviarCorreoInscripcion(correoDest, alumno.getNombreCompleto());
                            }
                        } catch (Exception e) {
                            log.warn("No se pudo enviar correo de inscripción a {}: {}", alumno.getMatricula(), e.getMessage());
                        }
                    } else {
                        alumnoRepository.save(alumno);
                        actualizados++;
                    }

                } catch (Exception e) {
                    log.warn("Error en fila {} (matrícula {}): {}", i + 1, getCellValue(row, 0), e.getMessage());
                    errores.add(Map.of("fila", i + 1, "matricula", matricula, "mensaje", e.getMessage()));
                    omitidos++;
                }
            }

            resultado.put("creados", creados);
            resultado.put("actualizados", actualizados);
            resultado.put("omitidos", omitidos);
            resultado.put("errores", errores);
            resultado.put("totalProcesados", creados + actualizados);

        } catch (Exception e) {
            log.error("Error al procesar archivo Excel: {}", e.getMessage(), e);
            resultado.put("error", "Error al procesar el archivo: " + e.getMessage());
        }

        return resultado;
    }

    private String getCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        java.util.Date d = cell.getDateCellValue();
                        if (d != null) {
                            yield org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.getNumericCellValue()).toInstant()
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT);
                        }
                    } catch (Exception ignored) {}
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf((long) cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    private Alumno.Sexo parseSexo(String s) {
        if (s == null || s.isBlank()) return null;
        return SEXO_MAP.get(s.trim());
    }

    private Alumno.Turno parseTurno(String s) {
        if (s == null || s.isBlank()) return null;
        return TURNO_MAP.get(s.trim());
    }

    private Alumno.EstatusMatricula parseEstatus(String s) {
        if (s == null || s.isBlank()) return null;
        return ESTATUS_MAP.get(s.trim());
    }

    private Usuario crearUsuarioAlumno(String correo) {
        Usuario usuario = new Usuario();
        usuario.setEmail(correo != null && !correo.isBlank() ? correo : "alumno_" + UUID.randomUUID().toString().substring(0, 8) + "@idee.edu.mx");
        usuario.setPassword(passwordEncoder.encode(PASSWORD_ALUMNO));
        usuario.setTipoUsuario(Usuario.TipoUsuario.ALUMNO);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }
}
