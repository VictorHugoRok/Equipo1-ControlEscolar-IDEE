package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.FirmaResponsableDTO;
import com.idee.controlescolar.model.ConfiguracionInstitucional;
import com.idee.controlescolar.model.ResponsableFirma;
import com.idee.controlescolar.model.TituloElectronico;
import com.idee.controlescolar.repository.ConfiguracionInstitucionalRepository;
import com.idee.controlescolar.repository.ResponsableFirmaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de alto nivel para generar títulos electrónicos firmados digitalmente
 * según el estándar DOF (13-abr-2018).
 *
 * Este servicio integra:
 * - Generación de cadena original (XmlGeneratorService)
 * - Firma digital con certificados SAT (FirmaDigitalService)
 * - Generación del XML final con nodos FirmaResponsable completos
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TituloElectronicoFirmadoService {

    private final FirmaDigitalService firmaDigitalService;
    private final XmlGeneratorService xmlGeneratorService;
    private final ConfiguracionInstitucionalRepository configuracionRepository;
    private final ResponsableFirmaRepository responsableFirmaRepository;

    /**
     * Genera un título electrónico firmado digitalmente.
     *
     * Este es el método principal que coordina todo el proceso:
     * 1. Obtiene la configuración institucional y responsables
     * 2. Genera la cadena original del título
     * 3. Para cada responsable, genera su firma digital (sello)
     * 4. Extrae la información del certificado
     * 5. Construye el XML final con las firmas incluidas
     *
     * @param titulo TituloElectronico a firmar
     * @return XML del título firmado digitalmente
     */
    public String generarTituloFirmado(TituloElectronico titulo) throws Exception {
        log.info("Generando título electrónico firmado para alumno: {}", titulo.getAlumno().getCurp());

        // 1. Obtener configuración institucional activa
        ConfiguracionInstitucional configuracion = configuracionRepository.findByActivoTrue()
            .orElseThrow(() -> new IllegalStateException("No existe configuración institucional activa"));

        // Validar que hay certificados cargados
        if (configuracion.getCertificadoData() == null || configuracion.getLlavePrivadaData() == null) {
            throw new IllegalStateException("No hay certificados cargados en la configuración institucional");
        }

        // 2. Obtener responsables de firma activos (ordenados por ordenFirma)
        List<ResponsableFirma> responsables = responsableFirmaRepository.findByActivoTrueOrderByOrdenFirma();

        if (responsables.isEmpty()) {
            throw new IllegalStateException("No hay responsables de firma activos");
        }

        log.info("Responsables de firma encontrados: {}", responsables.size());

        // 3. Generar cadena original según estándar DOF
        String cadenaOriginal = xmlGeneratorService.generarCadenaOriginal(titulo, responsables, configuracion);

        // Validar encoding UTF-8
        byte[] utf8Bytes = cadenaOriginal.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String cadenaVerificada = new String(utf8Bytes, java.nio.charset.StandardCharsets.UTF_8);

        log.info("=== CADENA ORIGINAL GENERADA ===");
        log.info("Longitud: {} caracteres", cadenaOriginal.length());
        log.info("Bytes UTF-8: {} bytes", utf8Bytes.length);
        log.info("Inicia con ||: {}", cadenaOriginal.startsWith("||"));
        log.info("Termina con ||: {}", cadenaOriginal.endsWith("||"));
        log.debug("Cadena completa: {}", cadenaOriginal);

        // Validar que no hay caracteres mal codificados
        if (!cadenaOriginal.equals(cadenaVerificada)) {
            log.warn("⚠ ADVERTENCIA: La cadena tiene problemas de encoding UTF-8");
        }

        // Guardar cadena original en archivo para verificación manual con acentos correctos
        try {
            String nombreArchivo = "cadena_original_" + titulo.getFolioControl().replace("-", "_") + ".txt";
            java.nio.file.Path archivoDebug = java.nio.file.Paths.get("titulos_generados", nombreArchivo);

            // Asegurar que el directorio existe
            java.nio.file.Files.createDirectories(archivoDebug.getParent());

            // Guardar con UTF-8 explícito
            java.nio.file.Files.writeString(
                archivoDebug,
                cadenaOriginal,
                java.nio.charset.StandardCharsets.UTF_8
            );

            log.info("Cadena original guardada en: {}", archivoDebug.toAbsolutePath());
            log.info("→ Abre este archivo para verificar que los acentos sean correctos");
        } catch (Exception e) {
            log.warn("No se pudo guardar cadena original para debug: {}", e.getMessage());
        }

        // 4. Generar firmas digitales para cada responsable
        List<FirmaResponsableDTO> firmas = new ArrayList<>();

        for (ResponsableFirma responsable : responsables) {
            FirmaResponsableDTO firmaDTO = generarFirmaResponsable(
                responsable,
                cadenaOriginal,
                configuracion.getCertificadoData(),
                configuracion.getLlavePrivadaData(),
                configuracion.getPasswordLlavePrivada()
            );
            firmas.add(firmaDTO);

            log.info("Firma generada para responsable: {} {}",
                responsable.getNombre(), responsable.getPrimerApellido());
        }

        // 5. Generar XML final con las firmas incluidas
        String xmlFirmado = generarXmlConFirmas(titulo, firmas, responsables, configuracion);

        log.info("Título electrónico firmado generado exitosamente");

        return xmlFirmado;
    }

    /**
     * Genera la firma completa para un responsable según el estándar DOF.
     *
     * Genera los 3 atributos principales del nodo FirmaResponsable:
     * - sello: firma digital en Base64
     * - certificadoResponsable: certificado en Base64
     * - noCertificadoResponsable: número de serie del certificado
     */
    private FirmaResponsableDTO generarFirmaResponsable(
            ResponsableFirma responsable,
            String cadenaOriginal,
            byte[] certificadoData,
            byte[] llavePrivadaData,
            String password) throws Exception {

        log.info("Generando firma para responsable: {} (CURP: {})",
            responsable.getNombre(), responsable.getCurp());

        // 1. Generar SELLO (atributo "sello")
        //    Firma RSA-SHA256 de la cadena original
        String sello = firmaDigitalService.generarSelloDesdeBytes(
            cadenaOriginal,
            llavePrivadaData,
            password
        );

        // 2. Obtener certificado en Base64 (atributo "certificadoResponsable")
        String certificadoBase64 = firmaDigitalService.obtenerCertificadoBase64DesdeBytes(certificadoData);

        // 3. Extraer número de certificado (atributo "noCertificadoResponsable")
        String noCertificado = firmaDigitalService.extraerNumeroCertificadoDesdeBytes(certificadoData);

        // 4. Crear DTO con toda la información
        return new FirmaResponsableDTO(
            sello,
            certificadoBase64,
            noCertificado,
            responsable.getCurp(),
            responsable.getIdCargo(),
            responsable.getAbrTitulo(),
            responsable.getNombre(),
            responsable.getPrimerApellido(),
            responsable.getSegundoApellido()
        );
    }

    /**
     * Genera el XML completo del título con las firmas digitales incluidas.
     * Construye el nodo FirmaResponsable con todos los atributos según estándar DOF.
     */
    private String generarXmlConFirmas(
            TituloElectronico titulo,
            List<FirmaResponsableDTO> firmas,
            List<ResponsableFirma> responsables,
            ConfiguracionInstitucional configuracion) {

        StringBuilder xml = new StringBuilder();

        // Declaración XML
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Elemento raíz
        xml.append("<TituloElectronico xmlns=\"https://www.siged.sep.gob.mx/titulos/\" ");
        xml.append("version=\"1.0\" ");
        xml.append("folioControl=\"").append(xmlGeneratorService.escaparXml(titulo.getFolioControl())).append("\">\n");

        // FirmaResponsables (con las firmas generadas)
        // IMPORTANTE: Usar el mismo formato que XmlGeneratorService (atributos en FirmaResponsable, no nodos hijo)
        xml.append("\t<FirmaResponsables>\n");

        for (int i = 0; i < responsables.size(); i++) {
            ResponsableFirma r = responsables.get(i);
            FirmaResponsableDTO f = firmas.get(i);

            xml.append("\t\t<FirmaResponsable ");
            xml.append("nombre=\"").append(xmlGeneratorService.escaparXml(r.getNombre())).append("\" ");
            xml.append("primerApellido=\"").append(xmlGeneratorService.escaparXml(r.getPrimerApellido())).append("\" ");

            if (r.getSegundoApellido() != null && !r.getSegundoApellido().isEmpty()) {
                xml.append("segundoApellido=\"").append(xmlGeneratorService.escaparXml(r.getSegundoApellido())).append("\" ");
            }

            xml.append("curp=\"").append(xmlGeneratorService.escaparXml(r.getCurp())).append("\" ");
            xml.append("idCargo=\"").append(xmlGeneratorService.escaparXml(r.getIdCargo())).append("\" ");
            xml.append("cargo=\"").append(xmlGeneratorService.escaparXml(r.getCargo())).append("\" ");

            if (r.getAbrTitulo() != null && !r.getAbrTitulo().isEmpty()) {
                xml.append("abrTitulo=\"").append(xmlGeneratorService.escaparXml(r.getAbrTitulo())).append("\" ");
            }

            // Atributos firmados (sello, certificado, noCertificado)
            xml.append("sello=\"").append(f.getSello()).append("\" ");
            xml.append("certificadoResponsable=\"").append(f.getCertificadoResponsable()).append("\" ");
            xml.append("noCertificadoResponsable=\"").append(f.getNoCertificadoResponsable()).append("\"/>\n");
        }

        xml.append("\t</FirmaResponsables>\n");

        // Resto de nodos usando el servicio existente
        // (Institucion, Carrera, Profesionista, Expedicion, Antecedente)
        String xmlCompleto = xmlGeneratorService.generarXmlTitulo(titulo, responsables, configuracion);

        // Extraer solo los nodos después de FirmaResponsables
        int inicioInstitucion = xmlCompleto.indexOf("<Institucion");
        int finTitulo = xmlCompleto.indexOf("</TituloElectronico>");

        if (inicioInstitucion > 0 && finTitulo > 0) {
            String nodosRestantes = xmlCompleto.substring(inicioInstitucion, finTitulo);
            xml.append("\t").append(nodosRestantes.replace("\n", "\n\t"));
        }

        xml.append("</TituloElectronico>");

        return xml.toString();
    }

    /**
     * Valida que un título pueda ser firmado.
     * Verifica que exista configuración, certificados y responsables.
     */
    public void validarRequisitosParaFirmar() throws IllegalStateException {
        // Verificar configuración activa
        ConfiguracionInstitucional config = configuracionRepository.findByActivoTrue()
            .orElseThrow(() -> new IllegalStateException(
                "No existe configuración institucional activa. Configure primero los datos de la institución."));

        // Verificar certificados
        if (config.getCertificadoData() == null || config.getLlavePrivadaData() == null) {
            throw new IllegalStateException(
                "No hay certificados SAT cargados. Suba el archivo .cer y .key en la configuración.");
        }

        // Verificar responsables
        List<ResponsableFirma> responsables = responsableFirmaRepository.findByActivoTrueOrderByOrdenFirma();
        if (responsables.isEmpty()) {
            throw new IllegalStateException(
                "No hay responsables de firma activos. Registre al menos un responsable de firma.");
        }

        log.info("Requisitos para firmar títulos: OK");
    }

    /**
     * Obtiene información del certificado actual para mostrar en UI.
     */
    public String obtenerInfoCertificado() throws Exception {
        ConfiguracionInstitucional config = configuracionRepository.findByActivoTrue()
            .orElseThrow(() -> new IllegalStateException("No existe configuración activa"));

        if (config.getCertificadoData() == null) {
            throw new IllegalStateException("No hay certificado cargado");
        }

        return firmaDigitalService.obtenerInfoCertificadoDesdeBytes(config.getCertificadoData());
    }
}
