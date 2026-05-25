package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.CertificadoElectronicoRequest;
import com.idee.controlescolar.dto.CertificadoElectronicoResponse;
import com.idee.controlescolar.dto.CertificadoElectronicoBatchRequest;
import com.idee.controlescolar.dto.CertificadoElectronicoBatchResponse;
import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Base64;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de negocio para certificados electrónicos (DEC).
 * Orquesta generación de XML, cadena original vía XSLT, firma digital y persistencia.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CertificadoElectronicoService {

    private static final String DIRECTORIO_CERTIFICADOS = "certificados_generados";
    private static final DateTimeFormatter FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String PREFIX_FOLIO = "CERT";
    /**
     * Lock por prefijo de folio para evitar colisiones en generación paralela.
     * Si se generan 10 certificados al mismo tiempo con el mismo prefijo,
     * sin este lock se puede calcular el mismo "siguiente folio" y fallar por unique constraint.
     */
    private static final Map<String, Object> FOLIO_LOCKS = new ConcurrentHashMap<>();

    private final CertificadoElectronicoRepository certificadoRepository;
    private final AlumnoRepository alumnoRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final PlantelRepository plantelRepository;
    private final ConfiguracionInstitucionalRepository configuracionRepository;
    private final ResponsableFirmaRepository responsableRepository;
    private final CalificacionRepository calificacionRepository;

    private final CertificadoXmlGeneratorService xmlGeneratorService;
    private final FirmaDigitalService firmaDigitalService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Genera un certificado electrónico (DEC): XML, cadena original, firma y guardado.
     */
    @Transactional
    public CertificadoElectronicoResponse generarCertificado(CertificadoElectronicoRequest request) {
        log.info("Generando certificado DEC para alumno: {}, programa: {}", request.getAlumnoId(), request.getProgramaId());

        Alumno alumno = alumnoRepository.findById(request.getAlumnoId())
                .orElseThrow(() -> new RuntimeException("Alumno no encontrado"));
        ProgramaEducativo programa = programaRepository.findById(request.getProgramaId())
                .orElseThrow(() -> new RuntimeException("Programa educativo no encontrado"));

        if (alumno.getFechaNacimiento() == null) {
            throw new RuntimeException("El alumno debe tener fecha de nacimiento registrada para generar certificados (requerido por XSD SEP)");
        }
        if (alumno.getSexo() == null) {
            throw new RuntimeException("El alumno debe tener sexo registrado para generar certificados (requerido por XSD SEP)");
        }

        boolean esTotal = "79".equals(request.getIdTipoCertificacion());
        int duracionPeriodos = (programa.getDuracionPeriodos() != null && programa.getDuracionPeriodos() > 0)
                ? programa.getDuracionPeriodos() : 0;

        // Calcular periodos aprobados reales: contar periodos distintos del plan donde el alumno tiene al menos una materia aprobada
        int periodosAprobadosReales = contarPeriodosAprobadosReales(alumno.getId(), programa.getId());

        if (esTotal) {
            // Certificado total: el alumno debe tener todos los periodos del programa aprobados
            boolean cumpleTotal = Alumno.EstatusMatricula.EGRESADO.equals(alumno.getEstatusMatricula())
                    || (periodosAprobadosReales >= duracionPeriodos);
            if (!cumpleTotal) {
                throw new RuntimeException("Para generar un certificado total, el alumno debe tener todos los periodos del programa aprobados ("
                        + duracionPeriodos + " periodos). El alumno tiene " + periodosAprobadosReales + " periodos aprobados.");
            }
        } else {
            // Certificado parcial: el alumno debe tener al menos 1 periodo cursado y aprobado
            if (periodosAprobadosReales < 1) {
                throw new RuntimeException("Para generar un certificado parcial, el alumno debe tener al menos 1 periodo cursado y aprobado. "
                        + "El alumno tiene " + periodosAprobadosReales + " periodos aprobados (verifique que las asignaturas tengan periodo del plan asignado).");
            }
        }

        ConfiguracionInstitucional config = configuracionRepository.findFirstByActivoTrueOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("No existe configuración institucional activa"));

        List<ResponsableFirma> responsables = responsableRepository.findByActivoTrueOrderByOrdenFirmaAsc();
        if (responsables.isEmpty()) {
            throw new RuntimeException("No hay responsables de firma configurados");
        }
        ResponsableFirma responsable = responsables.get(0);

        // Calificaciones: por periodo si es parcial, o todas si es total
        // Solo se usan calificaciones CONFIRMADAS por secretaría académica (estatus final ya definido)
        List<Calificacion> calificaciones;
        if (request.getPeriodo() != null && !request.getPeriodo().isBlank()) {
            calificaciones = calificacionRepository.findByAlumnoIdAndPeriodo(alumno.getId(), request.getPeriodo());
        } else {
            calificaciones = calificacionRepository.findByAlumnoId(alumno.getId()).stream()
                    .filter(c -> c.getAsignatura() != null && c.getAsignatura().getPrograma() != null
                            && c.getAsignatura().getPrograma().getId().equals(programa.getId()))
                    .collect(Collectors.toList());
        }
        calificaciones = calificaciones.stream()
                .filter(c -> Boolean.TRUE.equals(c.getConfirmada()))
                .collect(Collectors.toList());
        calificaciones = calificaciones.stream()
                .filter(c -> c.getAsignatura() != null && c.getAsignatura().cuentaEnPlanAcademico())
                .collect(Collectors.toList());

        // Requisito general: todas las calificaciones deben ser aprobatorias para generar cualquier certificado
        for (Calificacion c : calificaciones) {
            if (!esAprobatoria(c)) {
                throw new RuntimeException("Para generar cualquier tipo de certificado, todas las calificaciones deben ser aprobatorias. "
                        + "Existe al menos una calificación no aprobada (asignatura: "
                        + (c.getAsignatura() != null ? c.getAsignatura().getNombre() : "N/A") + ").");
            }
        }

        if (calificaciones.isEmpty()) {
            throw new RuntimeException("El alumno no tiene calificaciones confirmadas en este programa. La secretaría académica debe verificar y confirmar las calificaciones antes de generar el certificado.");
        }

        // Total de asignaturas del programa que cuentan (OBLIGATORIA, OPTATIVA, LIBRE).
        // EXTRACURRICULAR, SERVICIO_SOCIAL y RESIDENCIA_PROFESIONAL no se incluyen ni cuentan.
        List<Asignatura> asignaturasPrograma = programa.getAsignaturas();
        if (asignaturasPrograma == null || asignaturasPrograma.isEmpty()) {
            throw new RuntimeException("El programa debe tener asignaturas registradas para generar certificados.");
        }
        List<Asignatura> asignaturasQueCuentan = asignaturasPrograma.stream()
                .filter(Asignatura::cuentaEnPlanAcademico)
                .toList();
        int totalAsignaturas = asignaturasQueCuentan.size();
        int totalCreditos = asignaturasQueCuentan.stream()
                .mapToInt(a -> a.getCreditos() != null ? a.getCreditos() : 0)
                .sum();
        if (totalCreditos <= 0 && (programa.getCreditosTotales() == null || programa.getCreditosTotales() <= 0)) {
            throw new RuntimeException("El programa debe tener créditos totales registrados para generar certificados. Configure el campo 'Créditos totales' en el programa educativo.");
        }
        if (totalCreditos <= 0) {
            totalCreditos = programa.getCreditosTotales();
        }

        List<CertificadoXmlGeneratorService.AsignaturaItem> items = new ArrayList<>();
        int creditosObtenidos = 0;
        double sumaCalifAcreditadas = 0;
        for (Calificacion c : calificaciones) {
            Asignatura asig = c.getAsignatura();
            if (asig == null) continue;
            if (!asig.cuentaEnPlanAcademico()) continue;

            CertificadoXmlGeneratorService.AsignaturaItem item = new CertificadoXmlGeneratorService.AsignaturaItem();
            item.idAsignatura = (asig.getIdAsignatura() != null && !asig.getIdAsignatura().isBlank())
                    ? asig.getIdAsignatura() : String.valueOf(asig.getId());
            item.claveAsignatura = asig.getClave();
            item.nombre = asig.getNombre();
            item.periodoNumero = asig.getPeriodoNumero();
            String periodoRaw = c.getPeriodo() != null ? c.getPeriodo() : (request.getPeriodo() != null ? request.getPeriodo() : "");
            item.ciclo = normalizarCicloParaCertificado(periodoRaw, programa);
            if (c.getCalificacionFinal() != null) {
                // 2 decimales sin redondear (truncar) para reflejar exactamente lo capturado.
                item.calificacion = BigDecimal.valueOf(c.getCalificacionFinal()).setScale(2, RoundingMode.DOWN).toPlainString();
            } else {
                item.calificacion = "0.00";
            }
            item.idObservaciones = c.getIdObservaciones() != null ? c.getIdObservaciones() : ObservacionCalificacion.ID_DEFAULT;
            item.observaciones = ObservacionCalificacion.getDescripcionPorId(item.idObservaciones);
            if (item.observaciones == null || item.observaciones.isBlank()) {
                item.observaciones = ObservacionCalificacion.getDescripcionPorId(ObservacionCalificacion.ID_DEFAULT);
            }
            if (item.observaciones == null) item.observaciones = "ORDINARIO";
            item.idTipoAsignatura = mapTipoAsignaturaToId(asig.getTipo());
            item.tipoAsignatura = asig.getTipo() != null ? asig.getTipo().name() : "";
            item.creditos = asig.getCreditos() != null ? String.valueOf(asig.getCreditos()) : "0";
            items.add(item);
            // Solo sumar créditos si la asignatura tiene calificación aprobatoria
            if (esAprobatoria(c)) {
                creditosObtenidos += (asig.getCreditos() != null ? asig.getCreditos() : 0);
            }
            sumaCalifAcreditadas += (c.getCalificacionFinal() != null ? c.getCalificacionFinal() : 0);
        }

        if (items.isEmpty()) {
            throw new RuntimeException("El alumno no tiene calificaciones que cuenten para el certificado. No se incluyen: EXTRACURRICULAR, SERVICIO_SOCIAL ni RESIDENCIA_PROFESIONAL.");
        }

        // Validación por avance (porcentaje aprobado) para tipo de certificado:
        // - Total (79): solo si el alumno terminó el programa (100% de créditos aprobados)
        // - Parcial (80): solo si el alumno NO ha terminado (menos del 100%)
        double porcentajeAprobado = totalCreditos > 0 ? (100.0 * creditosObtenidos / totalCreditos) : 0.0;
        // Evitar pequeños errores por redondeo o créditos inconsistentes
        if (porcentajeAprobado > 100.0) porcentajeAprobado = 100.0;
        final double EPS = 0.0001;
        if (esTotal) {
            if (porcentajeAprobado + EPS < 100.0) {
                throw new RuntimeException("Para generar un certificado total, el alumno debe tener el 100% de su porcentaje aprobado. "
                        + "Actualmente tiene " + String.format("%.2f", porcentajeAprobado) + "%.");
            }
        } else {
            if (porcentajeAprobado + EPS >= 100.0) {
                throw new RuntimeException("Para generar un certificado parcial, el alumno debe tener menos del 100% de su porcentaje aprobado. "
                        + "Actualmente tiene " + String.format("%.2f", porcentajeAprobado) + "%.");
            }
        }

        // Ordenar asignaturas por periodo del plan (1°, 2°, 3°...) y luego por clave/id para estabilidad.
        items.sort(Comparator
                .comparingInt((CertificadoXmlGeneratorService.AsignaturaItem it) -> it.periodoNumero != null ? it.periodoNumero : Integer.MAX_VALUE)
                .thenComparing(it -> it.claveAsignatura != null ? it.claveAsignatura : "")
                .thenComparing(it -> it.idAsignatura != null ? it.idAsignatura : ""));

        // asignadas = número de asignaturas acreditadas (aprobadas) por el alumno que cuentan
        int asignadas = items.size();
        // promedio = promedio de las asignaturas acreditadas que cuentan
        String promedio = asignadas > 0 ? String.format("%.2f", sumaCalifAcreditadas / asignadas) : "0";
        // numeroCiclos: Parcial = periodos aprobados reales; Total = duracionPeriodos solo si todos están aprobados
        int numeroCiclos;
        if (esTotal) {
            if (duracionPeriodos > 0 && periodosAprobadosReales >= duracionPeriodos) {
                numeroCiclos = duracionPeriodos;
            } else {
                // EGRESADO con menos periodos en sistema, o duracionPeriodos no definido: usar lo aprobado
                numeroCiclos = periodosAprobadosReales > 0 ? periodosAprobadosReales : 1;
            }
        } else {
            numeroCiclos = periodosAprobadosReales > 0 ? periodosAprobadosReales : 1;
        }

        // Plantel emisor: mismo criterio que el XML (folio debe usar abreviatura del plantel, no solo la config global).
        Plantel plantelEmisor = plantelRepository.findById(request.getPlantelId())
                .orElseThrow(() -> new RuntimeException("Plantel emisor no encontrado"));
        if (plantelEmisor.getIdPlantel() == null || plantelEmisor.getIdPlantel().isBlank()) {
            throw new RuntimeException("El plantel seleccionado no tiene ID Plantel configurado. Configure el ID Plantel en Config. Institucional > Registro de plantel.");
        }

        CertificadoElectronico cert = new CertificadoElectronico();
        cert.setFolioControl(generarFolioControl(config, programa, plantelEmisor, request.getFechaExpedicion()));
        cert.setAlumno(alumno);
        cert.setPrograma(programa);
        cert.setFechaExpedicion(request.getFechaExpedicion());
        cert.setIdTipoCertificado(request.getIdTipoCertificacion());
        cert.setTipoCertificado(request.getTipoCertificacion() != null ? request.getTipoCertificacion() : (request.getIdTipoCertificacion().equals("79") ? "Total" : "Parcial"));
        cert.setPeriodo(request.getPeriodo());
        cert.setCicloEscolar(request.getCicloEscolar());
        cert.setEstatus(EstatusCertificado.GENERADO);
        cert.setObservaciones(request.getObservaciones());

        String certificadoBase64;
        String noCertificado;
        try {
            if (config.getCertificadoData() != null && config.getCertificadoData().length > 0) {
                certificadoBase64 = firmaDigitalService.obtenerCertificadoBase64DesdeBytes(config.getCertificadoData());
                noCertificado = firmaDigitalService.extraerNumeroCertificadoParaCertificadoElectronico(config.getCertificadoData());
            } else {
                certificadoBase64 = responsable.getCertificadoResponsable() != null ? responsable.getCertificadoResponsable() : "";
                noCertificado = responsable.getNoCertificadoResponsable() != null ? responsable.getNoCertificadoResponsable() : "";
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener certificado desde config, usando responsable: {}", e.getMessage());
            certificadoBase64 = responsable.getCertificadoResponsable() != null ? responsable.getCertificadoResponsable() : "";
            noCertificado = responsable.getNoCertificadoResponsable() != null ? responsable.getNoCertificadoResponsable() : "";
        }

        // idEntidad, idNombreInstitucion, nombrePlantel y claveCct: del plantel seleccionado en el formulario
        String idNombreInstitucionOverride = plantelEmisor.getIdPlantel();
        String nombrePlantel = plantelEmisor.getNombrePlantel() != null ? plantelEmisor.getNombrePlantel() : "";
        String idCampusOverride = plantelEmisor.getClaveDgp() != null ? plantelEmisor.getClaveDgp() : null;
        String campusOverride = (plantelEmisor.getCampus() != null && !plantelEmisor.getCampus().isBlank())
                ? plantelEmisor.getCampus()
                : nombrePlantel;

        // ========== PASO 1: Generar XML completo con todos los datos (nodos, ids, calificaciones, etc.)
        // El sello se deja como placeholder; se calcula al final para que la cadena original
        // incluya todos los datos ya definidos.
        String xmlConPlaceholder = xmlGeneratorService.generarXmlDec(
                cert, alumno, programa, config, responsable, items,
                totalAsignaturas, asignadas, promedio, String.valueOf(totalCreditos), String.valueOf(creditosObtenidos), numeroCiclos,
                "PENDIENTE_SELLO", certificadoBase64, noCertificado,
                idNombreInstitucionOverride, nombrePlantel,
                idCampusOverride, campusOverride);

        var resultadoXsd = xmlGeneratorService.validarContraXSDConErrores(xmlConPlaceholder);
        cert.setValidoXsd(resultadoXsd.valido);
        if (!resultadoXsd.valido) {
            cert.setErroresXsd(String.join("\n", resultadoXsd.errores));
            log.warn("El XML generado no pasó la validación XSD; se guarda de todos modos. Errores: {}", resultadoXsd.errores);
        }

        // ========== PASO 2: Generar cadena original a partir del XML completo (con todos los datos)
        String cadenaOriginal = xmlGeneratorService.generarCadenaOriginalConXslt(xmlConPlaceholder);
        cert.setCadenaOriginal(cadenaOriginal);

        // ========== PASO 3: Sello (ÚLTIMO PASO): se calcula solo cuando ya tenemos todos los datos
        // La cadena original incluye todo el XML; el sello es la firma digital de esa cadena.
        String selloBase64 = generarSelloCertificado(cadenaOriginal, config);
        cert.setSelloSat(selloBase64);
        cert.setEstatus(EstatusCertificado.FIRMADO);

        String xmlFinal = xmlConPlaceholder.replace("sello=\"PENDIENTE_SELLO\"", "sello=\"" + selloBase64 + "\"");
        cert.setXmlContent(xmlFinal);
        cert.setPlantel(plantelEmisor);

        try {
            Path dir = Paths.get(DIRECTORIO_CERTIFICADOS);
            Files.createDirectories(dir);
            String timestamp = LocalDateTime.now().format(FILENAME_FORMAT);
            String folioSanitizado = cert.getFolioControl().replace(" ", "_");
            String nombreArchivo = "certificado_" + folioSanitizado + "_" + timestamp + ".xml";
            Path archivo = dir.resolve(nombreArchivo);
            Files.writeString(archivo, xmlFinal, StandardCharsets.UTF_8);
            cert.setXmlPath(archivo.toAbsolutePath().toString());
            // Guardar cadena original en archivo para verificación y pruebas
            String nombreCadena = "cadena_original_" + folioSanitizado + "_" + timestamp + ".txt";
            Path archivoCadena = dir.resolve(nombreCadena);
            Files.writeString(archivoCadena, cadenaOriginal, StandardCharsets.UTF_8);
            log.info("Cadena original guardada en: {}", archivoCadena.toAbsolutePath());
        } catch (Exception e) {
            log.warn("No se pudo guardar archivo XML en disco: {}", e.getMessage());
        }

        certificadoRepository.save(cert);
        log.info("Certificado DEC guardado: folio {}", cert.getFolioControl());

        return toResponse(cert);
    }

    /**
     * Generación batch en un solo disparo (1..50 items).
     * Se procesa cada item en su propia transacción para que un fallo no reviente el batch completo.
     */
    public CertificadoElectronicoBatchResponse generarCertificadosBatch(CertificadoElectronicoBatchRequest request) {
        if (request == null) throw new RuntimeException("Solicitud inválida");
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Seleccione al menos un alumno");
        }
        if (request.getItems().size() > 50) {
            throw new RuntimeException("El máximo permitido es 50 certificados por generación.");
        }

        TransactionTemplate tpl = new TransactionTemplate(transactionManager);
        List<CertificadoElectronicoResponse> creados = new ArrayList<>();
        List<CertificadoElectronicoBatchResponse.FalloItem> errores = new ArrayList<>();

        for (CertificadoElectronicoBatchRequest.Item it : request.getItems()) {
            try {
                CertificadoElectronicoResponse r = tpl.execute(status -> {
                    CertificadoElectronicoRequest one = new CertificadoElectronicoRequest();
                    one.setAlumnoId(it.getAlumnoId());
                    one.setProgramaId(it.getProgramaId());
                    one.setPlantelId(it.getPlantelId());
                    one.setFechaExpedicion(request.getFechaExpedicion());
                    one.setIdTipoCertificacion(request.getIdTipoCertificacion());
                    one.setTipoCertificacion(request.getTipoCertificacion());
                    one.setPeriodo(request.getPeriodo());
                    one.setCicloEscolar(request.getCicloEscolar());
                    one.setObservaciones(request.getObservaciones());
                    return generarCertificado(one);
                });
                if (r != null) {
                    creados.add(r);
                } else {
                    errores.add(CertificadoElectronicoBatchResponse.FalloItem.builder()
                            .alumnoId(it.getAlumnoId())
                            .programaId(it.getProgramaId())
                            .plantelId(it.getPlantelId())
                            .mensaje("No se pudo generar el certificado.")
                            .build());
                }
            } catch (Exception e) {
                String msg = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : "Error al generar el certificado.";
                errores.add(CertificadoElectronicoBatchResponse.FalloItem.builder()
                        .alumnoId(it.getAlumnoId())
                        .programaId(it.getProgramaId())
                        .plantelId(it.getPlantelId())
                        .mensaje(msg)
                        .build());
            }
        }

        return CertificadoElectronicoBatchResponse.builder()
                .solicitados(request.getItems().size())
                .procesados(request.getItems().size())
                .creados(creados.size())
                .fallidos(errores.size())
                .certificadosCreados(creados)
                .errores(errores)
                .build();
    }

    @Transactional(readOnly = true)
    public CertificadoElectronicoResponse obtenerPorId(Long id) {
        CertificadoElectronico c = certificadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificado no encontrado"));
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<CertificadoElectronicoResponse> obtenerPorAlumno(Long alumnoId) {
        return certificadoRepository.findByAlumnoId(alumnoId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Devuelve todos los certificados existentes, ordenados del más reciente al más antiguo.
     */
    @Transactional(readOnly = true)
    public List<CertificadoElectronicoResponse> obtenerTodosOrdenadosRecientes() {
        return certificadoRepository
                .findAll(Sort.by(Sort.Direction.DESC, "fechaCreacion"))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Elimina todos los certificados electrónicos (uso para limpieza de datos de prueba).
     */
    @Transactional
    public void eliminarTodos() {
        certificadoRepository.deleteAll();
        log.warn("Todos los certificados electrónicos han sido eliminados (limpieza de datos).");
    }

    /**
     * Genera HTML para vista previa del certificado (usado internamente para PDF).
     * Incluye logos en el encabezado: SEP (izquierda) e institución (derecha).
     */
    @Transactional(readOnly = true)
    public String obtenerHtmlVistaPrevia(Long id) {
        CertificadoElectronico c = certificadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificado no encontrado"));
        if (Boolean.FALSE.equals(c.getValidoXsd())) {
            throw new RuntimeException("Este certificado no pasó la validación XSD. No se puede generar la vista previa.");
        }
        if (c.getXmlContent() == null || c.getXmlContent().isEmpty()) {
            throw new RuntimeException("El certificado no tiene contenido XML");
        }
        String claveCct = (c.getPlantel() != null && c.getPlantel().getClaveCct() != null)
                ? c.getPlantel().getClaveCct() : "";
        String html = xmlGeneratorService.generarHtmlVistaPrevia(c.getXmlContent(), claveCct);
        return inyectarLogosEnHtml(html);
    }

    /**
     * Carga los logos (SEP e institución) y los inyecta en el HTML del certificado.
     * Los logos deben estar en src/main/resources/images/:
     * - SEP_Logo.png (izquierda)
     * - logo.png (derecha, mismo que el login)
     */
    private static final String TRANSPARENT_1X1 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private String inyectarLogosEnHtml(String html) {
        String sepDataUri = cargarLogoComoDataUri("images/SEP_Logo.png");
        String institucionDataUri = cargarLogoComoDataUri("images/logo.png");
        html = html.replace("PLACEHOLDER_SEP_LOGO", sepDataUri != null ? sepDataUri : TRANSPARENT_1X1);
        html = html.replace("PLACEHOLDER_INSTITUCION_LOGO", institucionDataUri != null ? institucionDataUri : TRANSPARENT_1X1);
        return html;
    }

    private String cargarLogoComoDataUri(String classpathPath) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathPath);
            if (!resource.exists()) {
                log.debug("Logo no encontrado: {}", classpathPath);
                return null;
            }
            try (InputStream is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String mime = classpathPath.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                return "data:" + mime + ";base64," + base64;
            }
        } catch (Exception e) {
            log.warn("No se pudo cargar logo {}: {}", classpathPath, e.getMessage());
            return null;
        }
    }

    /**
     * Genera el PDF de vista previa del certificado (100% PDF, listo para visualizar o descargar).
     */
    public byte[] obtenerPdfVistaPrevia(Long id) {
        String html = obtenerHtmlVistaPrevia(id);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, "about:blank");
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Error al generar PDF del certificado: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar PDF del certificado: " + e.getMessage(), e);
        }
    }

    /**
     * Devuelve el contenido XML del certificado como bytes (para descarga).
     */
    public byte[] obtenerXmlComoBytes(Long id) {
        CertificadoElectronico c = certificadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificado no encontrado"));
        if (c.getXmlContent() == null || c.getXmlContent().isEmpty()) {
            return null;
        }
        return c.getXmlContent().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Devuelve la cadena original del certificado empaquetada en XML (solo para pruebas).
     * TEMPORAL: uso exclusivo para verificación y pruebas.
     */
    public byte[] obtenerCadenaOriginalComoXml(Long id) {
        CertificadoElectronico c = certificadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificado no encontrado"));
        String cadena = c.getCadenaOriginal();
        if (cadena == null) cadena = "";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cadenaOriginal><![CDATA[" + cadena + "]]></cadenaOriginal>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private CertificadoElectronicoResponse toResponse(CertificadoElectronico c) {
        return CertificadoElectronicoResponse.builder()
                .id(c.getId())
                .folioControl(c.getFolioControl())
                .alumnoId(c.getAlumno() != null ? c.getAlumno().getId() : null)
                .alumnoMatricula(c.getAlumno() != null ? c.getAlumno().getMatricula() : null)
                .alumnoNombreCompleto(c.getNombreCompletoAlumno())
                .alumnoCurp(c.getAlumno() != null ? c.getAlumno().getCurp() : null)
                .programaId(c.getPrograma() != null ? c.getPrograma().getId() : null)
                .programaClave(c.getPrograma() != null ? c.getPrograma().getClave() : null)
                .programaNombre(c.getPrograma() != null ? c.getPrograma().getNombre() : null)
                .fechaExpedicion(c.getFechaExpedicion())
                .idTipoCertificacion(c.getIdTipoCertificado())
                .tipoCertificacion(c.getTipoCertificado())
                .periodo(c.getPeriodo())
                .cicloEscolar(c.getCicloEscolar())
                .estatus(c.getEstatus())
                .estatusDescripcion(c.getEstatus() != null ? c.getEstatus().getDescripcion() : null)
                .xmlPath(c.getXmlPath())
                .tieneSello(c.getSelloSat() != null && !c.getSelloSat().isEmpty())
                .estaCompleto(c.estaCompleto())
                .validoXsd(c.getValidoXsd() != null ? c.getValidoXsd() : true)
                .erroresXsd(c.getErroresXsd() != null && !c.getErroresXsd().isEmpty()
                        ? java.util.Arrays.asList(c.getErroresXsd().split("\n"))
                        : null)
                .observaciones(c.getObservaciones())
                .fechaCreacion(c.getFechaCreacion())
                .fechaActualizacion(c.getFechaActualizacion())
                .build();
    }

    /**
     * Prefijo del folio: primero {@link Plantel#getNombreCorto()} del emisor (ej. IDEE en IES),
     * si no hay, {@link ConfiguracionInstitucional#getNombreCorto()}.
     */
    private String generarFolioControl(ConfiguracionInstitucional config, ProgramaEducativo programa,
                                       Plantel plantelEmisor, LocalDate fechaExpedicion) {
        String corto = "";
        if (plantelEmisor != null && plantelEmisor.getNombreCorto() != null && !plantelEmisor.getNombreCorto().isBlank()) {
            corto = plantelEmisor.getNombreCorto().trim();
        }
        if (corto.isBlank() && config != null && config.getNombreCorto() != null) {
            corto = config.getNombreCorto().trim();
        }
        if (corto.isBlank()) corto = "INST";
        corto = corto.replaceAll("[^A-Za-z0-9]", "").toUpperCase();

        String rvoe = programa != null && programa.getRvoe() != null ? programa.getRvoe().trim() : "";
        if (rvoe.isBlank()) rvoe = "SINRVOE";
        rvoe = rvoe.replaceAll("[^0-9]", "");
        if (rvoe.isBlank()) rvoe = "SINRVOE";

        LocalDate f = (fechaExpedicion != null) ? fechaExpedicion : LocalDate.now();
        String yy = String.format("%02d", f.getYear() % 100);

        String prefix = corto + "-" + rvoe + "-" + yy;
        Object lock = FOLIO_LOCKS.computeIfAbsent(prefix, k -> new Object());
        synchronized (lock) {
            int next = 1;
            try {
                var lastOpt = certificadoRepository.findTop1ByFolioControlStartingWithOrderByFolioControlDesc(prefix);
                if (lastOpt.isPresent() && lastOpt.get().getFolioControl() != null) {
                    String last = lastOpt.get().getFolioControl();
                    String suf = last.length() >= 4 ? last.substring(last.length() - 4) : "";
                    int n = Integer.parseInt(suf);
                    next = n + 1;
                }
            } catch (Exception ignored) {}

            String folio = prefix + String.format("%04d", next);
            // Garantizar unicidad si hay colisión (p. ej. datos viejos o corridas previas)
            int guard = 0;
            while (certificadoRepository.existsByFolioControl(folio) && guard++ < 500) {
                next++;
                folio = prefix + String.format("%04d", next);
            }
            return folio;
        }
    }

    /**
     * Genera el sello digital del certificado (último paso del flujo).
     * Se invoca solo cuando ya existen todos los datos del XML y la cadena original.
     * El sello es obligatorio: si no se puede generar, se lanza excepción.
     */
    private String generarSelloCertificado(String cadenaOriginal, ConfiguracionInstitucional config) {
        byte[] llaveData = config.getLlavePrivadaData();
        if (llaveData == null || llaveData.length == 0) {
            throw new RuntimeException("No hay llave privada configurada. Cargue el archivo .key en Config. Institucional > FIEL.");
        }
        String password = config.getPasswordLlavePrivada();
        if (password != null && !password.isEmpty()) {
            try {
                password = firmaDigitalService.desencriptarPassword(password);
            } catch (Exception e) {
                log.debug("Password no encriptado o desencriptación fallida, usando como texto plano: {}", e.getMessage());
            }
        }
        try {
            return firmaDigitalService.generarSelloDesdeBytes(cadenaOriginal, config.getLlavePrivadaData(), password != null ? password : "");
        } catch (Exception e) {
            log.error("Error al generar sello del certificado: {}", e.getMessage());
            throw new RuntimeException("No se pudo generar el sello digital. Verifique certificado, llave privada y contraseña en Config. Institucional: " + e.getMessage(), e);
        }
    }

    /**
     * Normaliza el ciclo al formato YYYY-N (año completo + número de periodo) según el tipo de periodo del programa.
     * - SEMESTRE: año con 1 y 2
     * - CUATRIMESTRE/TETRAMESTRE: año con 1, 2 y 3
     * - TRIMESTRE: año con 1, 2, 3 y 4
     */
    private static String normalizarCicloParaCertificado(String periodoRaw, ProgramaEducativo programa) {
        if (periodoRaw == null || periodoRaw.isBlank()) return "";
        String s = periodoRaw.trim();
        int año;
        int numero;
        // Ya en formato YYYY-N (ej. 2026-1, 2025-2)
        if (s.matches("\\d{4}-\\d+")) {
            String[] parts = s.split("-");
            año = Integer.parseInt(parts[0]);
            numero = Integer.parseInt(parts[1]);
        } else {
            // Formato tipo FEB-JUL-26-1, AGO-ENE-26-2: extraer YY y N del final
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("-(\\d{2})-(\\d+)$").matcher(s);
            if (m.find()) {
                int yy = Integer.parseInt(m.group(1));
                numero = Integer.parseInt(m.group(2));
                año = yy <= 50 ? 2000 + yy : 1900 + yy;
            } else {
                return s;
            }
        }
        int maxPeriodos = obtenerMaxPeriodosPorAnio(programa);
        if (numero < 1) numero = 1;
        if (numero > maxPeriodos) numero = maxPeriodos;
        return año + "-" + numero;
    }

    /** Máximo de periodos por año según tipo: SEMESTRE=2, CUATRIMESTRE/TETRAMESTRE=3, TRIMESTRE=4 */
    private static int obtenerMaxPeriodosPorAnio(ProgramaEducativo programa) {
        if (programa == null || programa.getTipoPeriodo() == null) return 2;
        return switch (programa.getTipoPeriodo()) {
            case SEMANAL -> 2;
            case SEMESTRE -> 2;
            case CUATRIMESTRE, TETRAMESTRE -> 3;
            case TRIMESTRE -> 4;
        };
    }

    /** IDs fijos por tipo de asignatura (SEP/catálogo institucional). */
    private static String mapTipoAsignaturaToId(Asignatura.TipoAsignatura tipo) {
        if (tipo == null) return "263";
        return switch (tipo) {
            case OBLIGATORIA -> "263";
            case OPTATIVA -> "264";
            case LIBRE -> "265";
            case EXTRACURRICULAR -> "266";
            case SERVICIO_SOCIAL -> "267";
            case RESIDENCIA_PROFESIONAL -> "268";
        };
    }

    /**
     * Indica si una calificación es aprobatoria (estatus APROBADO o calificación >= 7.00).
     * Escala: mínima 5, máxima 10, aprobatoria >= 7.00.
     * La calificación tiene prioridad sobre estatus (puede ser antiguo con umbral 70).
     */
    private static boolean esAprobatoria(Calificacion c) {
        if (Calificacion.EstatusCalificacion.APROBADO.equals(c.getEstatus())) return true;
        if (c.getCalificacionFinal() != null && c.getCalificacionFinal() >= 7.0) return true;
        return false;
    }

    /**
     * Cuenta los periodos distintos del plan donde el alumno tiene al menos una materia aprobada.
     * Usado para certificados parciales (numeroCiclos).
     */
    private int contarPeriodosAprobadosReales(Long alumnoId, Long programaId) {
        List<Calificacion> todas = calificacionRepository.findByAlumnoId(alumnoId).stream()
                .filter(c -> c.getAsignatura() != null
                        && c.getAsignatura().getPrograma() != null
                        && c.getAsignatura().getPrograma().getId().equals(programaId))
                .filter(c -> c.getAsignatura().cuentaEnPlanAcademico())
                .filter(CertificadoElectronicoService::esAprobatoria)
                .toList();
        Set<Integer> periodos = todas.stream()
                .map(c -> c.getAsignatura().getPeriodo())
                .filter(Objects::nonNull)
                .map(Periodo::getNumero)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return periodos.isEmpty() ? 1 : periodos.size();
    }
}
