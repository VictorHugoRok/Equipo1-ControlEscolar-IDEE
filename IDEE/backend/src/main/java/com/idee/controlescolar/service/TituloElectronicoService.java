package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.TituloElectronicoRequest;
import com.idee.controlescolar.dto.TituloElectronicoResponse;
import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio principal para la gestión de títulos profesionales electrónicos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TituloElectronicoService {

    private final TituloElectronicoRepository tituloRepository;
    private final AlumnoRepository alumnoRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final ResponsableFirmaRepository responsableRepository;
    private final ConfiguracionInstitucionalRepository configuracionRepository;
    private final UsuarioRepository usuarioRepository;

    private final XmlGeneratorService xmlGeneratorService;
    private final FirmaDigitalService firmaDigitalService;
    private final TituloElectronicoFirmadoService tituloFirmadoService;

    private static final String DIRECTORIO_TITULOS = "titulos_generados";
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Genera un nuevo título electrónico para un alumno.
     *
     * @param request Datos del título a generar
     * @return Respuesta con los datos del título generado
     */
    @Transactional
    public TituloElectronicoResponse generarTitulo(TituloElectronicoRequest request) {
        log.info("Iniciando generación de título para alumno ID: {}", request.getAlumnoId());

        // 1. Validar que existan los datos necesarios
        Alumno alumno = alumnoRepository.findById(request.getAlumnoId())
                .orElseThrow(() -> new RuntimeException("Alumno no encontrado"));

        ProgramaEducativo programa = programaRepository.findById(request.getProgramaId())
                .orElseThrow(() -> new RuntimeException("Programa educativo no encontrado"));

        // 2. Validar requisitos del alumno
        validarRequisitosAlumno(alumno);

        // 3. Obtener configuración y responsables
        ConfiguracionInstitucional configuracion = configuracionRepository.findByActivoTrue()
                .orElseThrow(() -> new RuntimeException("No existe configuración institucional activa"));

        List<ResponsableFirma> responsables = responsableRepository.findByActivoTrueOrderByOrdenFirmaAsc();
        if (responsables.isEmpty()) {
            throw new RuntimeException("No hay responsables de firma configurados");
        }

        // 4. Crear entidad TituloElectronico
        TituloElectronico titulo = new TituloElectronico();
        titulo.setFolioControl(generarFolioControl());
        titulo.setAlumno(alumno);
        titulo.setPrograma(programa);

        // Copiar datos del request
        titulo.setFechaExpedicion(request.getFechaExpedicion());
        titulo.setIdModalidadTitulacion(request.getIdModalidadTitulacion());
        titulo.setModalidadTitulacion(request.getModalidadTitulacion());
        titulo.setFechaExamenProfesional(request.getFechaExamenProfesional());
        titulo.setFechaExencionExamenProfesional(request.getFechaExencionExamenProfesional());

        titulo.setCumplioServicioSocial(request.getCumplioServicioSocial());
        titulo.setIdFundamentoLegalServicioSocial(request.getIdFundamentoLegalServicioSocial());
        titulo.setFundamentoLegalServicioSocial(request.getFundamentoLegalServicioSocial());

        titulo.setInstitucionProcedencia(request.getInstitucionProcedencia());
        titulo.setIdTipoEstudioAntecedente(request.getIdTipoEstudioAntecedente());
        titulo.setTipoEstudioAntecedente(request.getTipoEstudioAntecedente());
        titulo.setIdEntidadFederativaAntecedente(request.getIdEntidadFederativaAntecedente());
        titulo.setEntidadFederativaAntecedente(request.getEntidadFederativaAntecedente());
        titulo.setFechaInicioAntecedente(request.getFechaInicioAntecedente());
        titulo.setFechaTerminacionAntecedente(request.getFechaTerminacionAntecedente());
        titulo.setNoCedula(request.getNoCedula());

        titulo.setObservaciones(request.getObservaciones());
        titulo.setEstatus(EstatusTitulo.GENERADO);

        // 5. Generar cadena original
        String cadenaOriginal = xmlGeneratorService.generarCadenaOriginal(titulo, responsables, configuracion);
        titulo.setCadenaOriginal(cadenaOriginal);

        // 5.5. DIAGNÓSTICO: Verificar estado de certificados antes de firmar
        boolean tieneCerts = configuracion.tieneCertificados();
        log.info("=== DIAGNÓSTICO DE CERTIFICADOS ===");
        log.info("tieneCertificados() = {}", tieneCerts);
        log.info("cerBytes = {}", configuracion.getCertificadoData() == null ? 0 : configuracion.getCertificadoData().length);
        log.info("keyBytes = {}", configuracion.getLlavePrivadaData() == null ? 0 : configuracion.getLlavePrivadaData().length);
        log.info("hasPassword = {}", configuracion.getPasswordLlavePrivada() != null && !configuracion.getPasswordLlavePrivada().isBlank());
        log.info("===================================");

        // 6. Generar XML con firmas digitales completas (si hay certificados configurados)
        String xmlContent;
        if (tieneCerts) {
            try {
                log.info("Generando título con firmas digitales completas");
                // Usar el servicio de firma completo que genera el XML con todos los campos
                xmlContent = tituloFirmadoService.generarTituloFirmado(titulo);
                titulo.setEstatus(EstatusTitulo.FIRMADO);

                // El sello ya está incluido en el XML generado por el servicio de firma
                log.info("Título firmado digitalmente exitosamente con certificados en XML");
            } catch (Exception e) {
                log.error("Error al firmar título: {}", e.getMessage(), e);
                log.warn("Generando XML básico sin firmas digitales - el título quedará en estatus GENERADO");
                // Generar XML básico sin firmas
                xmlContent = xmlGeneratorService.generarXmlTitulo(titulo, responsables, configuracion);
                titulo.setEstatus(EstatusTitulo.GENERADO);
            }
        } else {
            log.info("Título generado sin firma digital (no hay certificados configurados)");
            // Generar XML básico sin firmas
            xmlContent = xmlGeneratorService.generarXmlTitulo(titulo, responsables, configuracion);
            titulo.setEstatus(EstatusTitulo.GENERADO);
        }

        titulo.setXmlContent(xmlContent);

        // 8. Guardar archivo XML
        String rutaArchivo = guardarArchivoXml(titulo, xmlContent);
        titulo.setXmlPath(rutaArchivo);

        // 9. Guardar en base de datos
        TituloElectronico tituloGuardado = tituloRepository.save(titulo);

        log.info("Título generado exitosamente: {}", tituloGuardado.getFolioControl());

        return convertirAResponse(tituloGuardado);
    }

    /**
     * Obtiene todos los títulos de un alumno.
     */
    @Transactional(readOnly = true)
    public List<TituloElectronicoResponse> obtenerTitulosPorAlumno(Long alumnoId) {
        return tituloRepository.findByAlumnoId(alumnoId).stream()
                .map(this::convertirAResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene un título por su folio de control.
     */
    @Transactional(readOnly = true)
    public TituloElectronicoResponse obtenerTituloPorFolio(String folioControl) {
        TituloElectronico titulo = tituloRepository.findByFolioControl(folioControl)
                .orElseThrow(() -> new RuntimeException("Título no encontrado con folio: " + folioControl));
        return convertirAResponse(titulo);
    }

    /**
     * Obtiene un título por ID.
     */
    @Transactional(readOnly = true)
    public TituloElectronicoResponse obtenerTituloPorId(Long id) {
        TituloElectronico titulo = tituloRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Título no encontrado"));
        return convertirAResponse(titulo);
    }

    /**
     * Actualiza el estatus de un título.
     */
    @Transactional
    public TituloElectronicoResponse actualizarEstatus(Long id, EstatusTitulo nuevoEstatus) {
        TituloElectronico titulo = tituloRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Título no encontrado"));

        titulo.setEstatus(nuevoEstatus);
        TituloElectronico actualizado = tituloRepository.save(titulo);

        log.info("Estatus del título {} actualizado a {}", titulo.getFolioControl(), nuevoEstatus);

        return convertirAResponse(actualizado);
    }

    /**
     * Descarga el XML de un título.
     */
    @Transactional(readOnly = true)
    public byte[] descargarXml(Long id) {
        TituloElectronico titulo = tituloRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Título no encontrado"));

        if (titulo.getXmlContent() == null || titulo.getXmlContent().isEmpty()) {
            throw new RuntimeException("El título no tiene contenido XML generado");
        }

        return titulo.getXmlContent().getBytes();
    }

    /**
     * Valida que el alumno cumpla con los requisitos para obtener título.
     */
    public boolean validarRequisitosAlumno(Long alumnoId) {
        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> new RuntimeException("Alumno no encontrado"));

        return validarRequisitosAlumno(alumno);
    }

    /**
     * Validación interna de requisitos del alumno.
     */
    private boolean validarRequisitosAlumno(Alumno alumno) {
        // Verificar que el alumno sea egresado
       Alumno.EstatusMatricula estatus = alumno.getEstatusMatricula();

if (estatus == null) {
    throw new RuntimeException("El alumno no tiene estatus de matrícula definido");
}

if (estatus != Alumno.EstatusMatricula.EGRESADO) {
    throw new RuntimeException(
        "El alumno no tiene estatus de EGRESADO (estatus actual: " + estatus.name() + ")"
    );
}



        // Verificar CURP
        if (alumno.getCurp() == null || alumno.getCurp().length() != 18) {
            throw new RuntimeException("El alumno no tiene un CURP válido");
        }

        // Verificar correo electrónico
        if (alumno.getCorreoInstitucional() == null && alumno.getCorreoPersonal() == null) {
            throw new RuntimeException("El alumno no tiene correo electrónico registrado");
        }

        // Aquí se pueden agregar más validaciones:
        // - Calificaciones aprobadas
        // - Documentos entregados
        // - Servicio social cumplido
        // etc.

        return true;
    }

    /**
     * Genera un folio de control único.
     */
    private String generarFolioControl() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "IDEE-" + timestamp + "-" + uuid;
    }

    /**
     * Guarda el archivo XML en el sistema de archivos.
     */
    private String guardarArchivoXml(TituloElectronico titulo, String xmlContent) {
        try {
            // Crear directorio si no existe
            Path directorioTitulos = Paths.get(DIRECTORIO_TITULOS);
            if (!Files.exists(directorioTitulos)) {
                Files.createDirectories(directorioTitulos);
            }

            // Generar nombre de archivo
            String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
            String nombreArchivo = String.format("titulo_%s_%s_%s.xml",
                    titulo.getAlumno().getMatricula(),
                    timestamp,
                    titulo.getFolioControl().replace("-", "_"));

            Path rutaArchivo = directorioTitulos.resolve(nombreArchivo);

            // Guardar archivo con codificación UTF-8
            try (FileWriter writer = new FileWriter(rutaArchivo.toFile(), StandardCharsets.UTF_8)) {
                writer.write(xmlContent);
            }

            log.info("Archivo XML guardado en: {}", rutaArchivo);
            return rutaArchivo.toString();

        } catch (IOException e) {
            log.error("Error al guardar archivo XML: {}", e.getMessage());
            throw new RuntimeException("Error al guardar archivo XML: " + e.getMessage());
        }
    }

    /**
     * Convierte una entidad TituloElectronico a DTO Response.
     */
    private TituloElectronicoResponse convertirAResponse(TituloElectronico titulo) {
        return TituloElectronicoResponse.builder()
                .id(titulo.getId())
                .folioControl(titulo.getFolioControl())
                .alumnoId(titulo.getAlumno().getId())
                .alumnoMatricula(titulo.getAlumno().getMatricula())
                .alumnoNombreCompleto(titulo.getNombreCompletoProfesionista())
                .alumnoCurp(titulo.getAlumno().getCurp())
                .alumnoCorreo(titulo.getAlumno().getCorreoInstitucional())
                .programaId(titulo.getPrograma().getId())
                .programaClave(titulo.getPrograma().getClave())
                .programaNombre(titulo.getPrograma().getNombre())
                .programaRvoe(titulo.getPrograma().getRvoe())
                .fechaExpedicion(titulo.getFechaExpedicion())
                .modalidadTitulacion(titulo.getModalidadTitulacion())
                .fechaExamenProfesional(titulo.getFechaExamenProfesional())
                .fechaExencionExamenProfesional(titulo.getFechaExencionExamenProfesional())
                .cumplioServicioSocial(titulo.getCumplioServicioSocial())
                .fundamentoLegalServicioSocial(titulo.getFundamentoLegalServicioSocial())
                .institucionProcedencia(titulo.getInstitucionProcedencia())
                .tipoEstudioAntecedente(titulo.getTipoEstudioAntecedente())
                .fechaTerminacionAntecedente(titulo.getFechaTerminacionAntecedente())
                .estatus(titulo.getEstatus())
                .estatusDescripcion(titulo.getEstatus().getDescripcion())
                .xmlPath(titulo.getXmlPath())
                .tieneSello(titulo.getSelloSat() != null && !titulo.getSelloSat().isEmpty())
                .estaCompleto(titulo.estaCompleto())
                .observaciones(titulo.getObservaciones())
                .fechaCreacion(titulo.getFechaCreacion())
                .fechaActualizacion(titulo.getFechaActualizacion())
                .build();
    }

    public Object firmar(Long id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'firmar'");
    }
}
