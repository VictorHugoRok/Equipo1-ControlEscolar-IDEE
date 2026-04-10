package com.idee.controlescolar.service;

import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.ConfiguracionInstitucional;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.model.ResponsableFirma;
import com.idee.controlescolar.model.TituloElectronico;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.XMLConstants;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio para generar XML de títulos electrónicos según estándar SEP.
 * Diario Oficial 13 de abril de 2018.
 */
@Service
@Slf4j
public class XmlGeneratorService {

    private static final String NAMESPACE = "https://www.siged.sep.gob.mx/titulos/";
    private static final String VERSION = "1.0";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Genera el XML completo de un título electrónico según estándar SEP.
     *
     * @param titulo Título electrónico a generar
     * @param responsables Lista de responsables que firmarán el título
     * @param configuracion Configuración institucional
     * @return String con el contenido XML generado
     */
    public String generarXmlTitulo(TituloElectronico titulo,
                                   List<ResponsableFirma> responsables,
                                   ConfiguracionInstitucional configuracion) {

        if (responsables == null || responsables.isEmpty()) {
            throw new IllegalArgumentException("Se requieren al menos un responsable de firma");
        }

        if (configuracion == null) {
            throw new IllegalArgumentException("Se requiere configuración institucional activa");
        }

        Alumno alumno = titulo.getAlumno();
        ProgramaEducativo programa = titulo.getPrograma();

        StringBuilder xml = new StringBuilder();

        // Declaración XML
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Elemento raíz TituloElectronico
        xml.append("<TituloElectronico xmlns=\"").append(NAMESPACE).append("\" ");
        xml.append("version=\"").append(VERSION).append("\" ");
        xml.append("folioControl=\"").append(escaparXml(titulo.getFolioControl())).append("\">\n");

        // FirmaResponsables
        xml.append("\t<FirmaResponsables>\n");
        for (ResponsableFirma responsable : responsables) {
            xml.append("\t\t<FirmaResponsable ");
            xml.append("nombre=\"").append(escaparXml(responsable.getNombre())).append("\" ");
            xml.append("primerApellido=\"").append(escaparXml(responsable.getPrimerApellido())).append("\" ");

            if (responsable.getSegundoApellido() != null && !responsable.getSegundoApellido().isEmpty()) {
                xml.append("segundoApellido=\"").append(escaparXml(responsable.getSegundoApellido())).append("\" ");
            }

            xml.append("curp=\"").append(escaparXml(responsable.getCurp())).append("\" ");
            xml.append("idCargo=\"").append(escaparXml(responsable.getIdCargo())).append("\" ");
            xml.append("cargo=\"").append(escaparXml(responsable.getCargo())).append("\" ");

            if (responsable.getAbrTitulo() != null && !responsable.getAbrTitulo().isEmpty()) {
                xml.append("abrTitulo=\"").append(escaparXml(responsable.getAbrTitulo())).append("\" ");
            }

            // Sello y certificado - se agregarán después de la firma digital
            xml.append("sello=\"\" ");
            xml.append("certificadoResponsable=\"");
            if (responsable.getCertificadoResponsable() != null) {
                xml.append(responsable.getCertificadoResponsable());
            }
            xml.append("\" ");

            xml.append("noCertificadoResponsable=\"");
            if (responsable.getNoCertificadoResponsable() != null) {
                xml.append(escaparXml(responsable.getNoCertificadoResponsable()));
            }
            xml.append("\"/>\n");
        }
        xml.append("\t</FirmaResponsables>\n");

        // Institucion
        xml.append("\t<Institucion ");
        xml.append("cveInstitucion=\"").append(escaparXml(configuracion.getCveInstitucion())).append("\" ");
        xml.append("nombreInstitucion=\"").append(escaparXml(configuracion.getNombreInstitucion())).append("\"/>\n");

        // Carrera
        xml.append("\t<Carrera ");
        xml.append("cveCarrera=\"").append(escaparXml(programa.getClave())).append("\" ");
        xml.append("nombreCarrera=\"").append(escaparXml(programa.getNombre())).append("\" ");

        if (programa.getFechaRvoe() != null) {
            xml.append("fechaInicio=\"").append(programa.getFechaRvoe().format(DATE_FORMATTER)).append("\" ");
        }

        xml.append("fechaTerminacion=\"").append(titulo.getFechaExpedicion().format(DATE_FORMATTER)).append("\" ");
        xml.append("idAutorizacionReconocimiento=\"1\" ");
        xml.append("autorizacionReconocimiento=\"RVOE FEDERAL\" ");

        if (programa.getRvoe() != null && !programa.getRvoe().isEmpty()) {
            xml.append("numeroRvoe=\"").append(escaparXml(programa.getRvoe())).append("\" ");
        }

        xml.append("/>\n");

        // Profesionista
        xml.append("\t<Profesionista ");
        xml.append("curp=\"").append(escaparXml(alumno.getCurp())).append("\" ");
        xml.append("nombre=\"").append(escaparXml(alumno.getNombre())).append("\" ");
        xml.append("primerApellido=\"").append(escaparXml(alumno.getApellidoPaterno())).append("\" ");

        if (alumno.getApellidoMaterno() != null && !alumno.getApellidoMaterno().isEmpty()) {
            xml.append("segundoApellido=\"").append(escaparXml(alumno.getApellidoMaterno())).append("\" ");
        }

        String correo = alumno.getCorreoInstitucional() != null ? alumno.getCorreoInstitucional() : alumno.getCorreoPersonal();
        xml.append("correoElectronico=\"").append(escaparXml(correo)).append("\"/>\n");

        // Expedicion
        xml.append("\t<Expedicion ");
        xml.append("fechaExpedicion=\"").append(titulo.getFechaExpedicion().format(DATE_FORMATTER)).append("\" ");
        xml.append("idModalidadTitulacion=\"").append(escaparXml(titulo.getIdModalidadTitulacion())).append("\" ");
        xml.append("modalidadTitulacion=\"").append(escaparXml(titulo.getModalidadTitulacion())).append("\" ");

        if (titulo.getFechaExamenProfesional() != null) {
            xml.append("fechaExamenProfesional=\"").append(titulo.getFechaExamenProfesional().format(DATE_FORMATTER)).append("\" ");
        }

        if (titulo.getFechaExencionExamenProfesional() != null) {
            xml.append("fechaExencionExamenProfesional=\"").append(titulo.getFechaExencionExamenProfesional().format(DATE_FORMATTER)).append("\" ");
        }

        xml.append("cumplioServicioSocial=\"").append(titulo.getCumplioServicioSocial() ? "1" : "0").append("\" ");

        if (titulo.getIdFundamentoLegalServicioSocial() != null) {
            xml.append("idFundamentoLegalServicioSocial=\"").append(escaparXml(titulo.getIdFundamentoLegalServicioSocial())).append("\" ");
        }

        if (titulo.getFundamentoLegalServicioSocial() != null) {
            xml.append("fundamentoLegalServicioSocial=\"").append(escaparXml(titulo.getFundamentoLegalServicioSocial())).append("\" ");
        }

        xml.append("idEntidadFederativa=\"").append(escaparXml(configuracion.getIdEntidadFederativa())).append("\" ");
        xml.append("entidadFederativa=\"").append(escaparXml(configuracion.getEntidadFederativa())).append("\"/>\n");

        // Antecedente
        xml.append("\t<Antecedente ");
        xml.append("institucionProcedencia=\"").append(escaparXml(titulo.getInstitucionProcedencia())).append("\" ");
        xml.append("idTipoEstudioAntecedente=\"").append(escaparXml(titulo.getIdTipoEstudioAntecedente())).append("\" ");
        xml.append("tipoEstudioAntecedente=\"").append(escaparXml(titulo.getTipoEstudioAntecedente())).append("\" ");
        xml.append("idEntidadFederativa=\"").append(escaparXml(titulo.getIdEntidadFederativaAntecedente())).append("\" ");
        xml.append("entidadFederativa=\"").append(escaparXml(titulo.getEntidadFederativaAntecedente())).append("\" ");

        if (titulo.getFechaInicioAntecedente() != null) {
            xml.append("fechaInicio=\"").append(titulo.getFechaInicioAntecedente().format(DATE_FORMATTER)).append("\" ");
        }

        xml.append("fechaTerminacion=\"").append(titulo.getFechaTerminacionAntecedente().format(DATE_FORMATTER)).append("\" ");

        if (titulo.getNoCedula() != null && !titulo.getNoCedula().isEmpty()) {
            xml.append("noCedula=\"").append(escaparXml(titulo.getNoCedula())).append("\" ");
        }

        xml.append("/>\n");

        // Cierre del elemento raíz
        xml.append("</TituloElectronico>");

        return xml.toString();
    }

    /**
     * Genera la cadena original para la firma digital según estándar SEP.
     * Sección 4.5 del estándar oficial.
     *
     * @param titulo Título electrónico
     * @param responsables Lista de responsables
     * @param configuracion Configuración institucional
     * @return Cadena original
     */
    public String generarCadenaOriginal(TituloElectronico titulo,
                                        List<ResponsableFirma> responsables,
                                        ConfiguracionInstitucional configuracion) {

        Alumno alumno = titulo.getAlumno();
        ProgramaEducativo programa = titulo.getPrograma();

        StringBuilder cadena = new StringBuilder();

        // Inicio de cadena original (doble pipe)
        cadena.append("||");

        // 1. Información del nodo TituloElectronico
        cadena.append(VERSION).append("|");
        cadena.append(titulo.getFolioControl()).append("|");

        // 2. Información del nodo FirmaResponsable (para cada responsable)
        for (ResponsableFirma responsable : responsables) {
            cadena.append(responsable.getCurp()).append("|");
            cadena.append(responsable.getIdCargo()).append("|");
            cadena.append(responsable.getCargo()).append("|");
            cadena.append(responsable.getAbrTitulo() != null ? responsable.getAbrTitulo() : "").append("|");
        }

        // 3. Información del nodo Institucion
        cadena.append(configuracion.getCveInstitucion()).append("|");
        cadena.append(configuracion.getNombreInstitucion()).append("|");

        // 4. Información del nodo Carrera
        cadena.append(programa.getClave()).append("|");
        cadena.append(programa.getNombre()).append("|");
        cadena.append(programa.getFechaRvoe() != null ? programa.getFechaRvoe().format(DATE_FORMATTER) : "").append("|");
        cadena.append(titulo.getFechaExpedicion().format(DATE_FORMATTER)).append("|");
        cadena.append("1").append("|"); // idAutorizacionReconocimiento
        cadena.append("RVOE FEDERAL").append("|"); // autorizacionReconocimiento
        cadena.append(programa.getRvoe() != null ? programa.getRvoe() : "").append("|");

        // 5. Información del nodo Profesionista
        cadena.append(alumno.getCurp()).append("|");
        cadena.append(alumno.getNombre()).append("|");
        cadena.append(alumno.getApellidoPaterno()).append("|");
        cadena.append(alumno.getApellidoMaterno() != null ? alumno.getApellidoMaterno() : "").append("|");
        String correo = alumno.getCorreoInstitucional() != null ? alumno.getCorreoInstitucional() : alumno.getCorreoPersonal();
        cadena.append(correo).append("|");

        // 6. Información del nodo Expedicion
        cadena.append(titulo.getFechaExpedicion().format(DATE_FORMATTER)).append("|");
        cadena.append(titulo.getIdModalidadTitulacion()).append("|");
        cadena.append(titulo.getModalidadTitulacion()).append("|");
        cadena.append(titulo.getFechaExamenProfesional() != null ? titulo.getFechaExamenProfesional().format(DATE_FORMATTER) : "").append("|");
        cadena.append(titulo.getFechaExencionExamenProfesional() != null ? titulo.getFechaExencionExamenProfesional().format(DATE_FORMATTER) : "").append("|");
        cadena.append(titulo.getCumplioServicioSocial() ? "1" : "0").append("|");
        cadena.append(titulo.getIdFundamentoLegalServicioSocial() != null ? titulo.getIdFundamentoLegalServicioSocial() : "").append("|");
        cadena.append(titulo.getFundamentoLegalServicioSocial() != null ? titulo.getFundamentoLegalServicioSocial() : "").append("|");
        cadena.append(configuracion.getIdEntidadFederativa()).append("|");
        cadena.append(configuracion.getEntidadFederativa()).append("|");

        // 7. Información del nodo Antecedente
        cadena.append(titulo.getInstitucionProcedencia()).append("|");
        cadena.append(titulo.getIdTipoEstudioAntecedente()).append("|");
        cadena.append(titulo.getTipoEstudioAntecedente()).append("|");
        cadena.append(titulo.getIdEntidadFederativaAntecedente()).append("|");
        cadena.append(titulo.getEntidadFederativaAntecedente()).append("|");
        cadena.append(titulo.getFechaInicioAntecedente() != null ? titulo.getFechaInicioAntecedente().format(DATE_FORMATTER) : "").append("|");
        cadena.append(titulo.getFechaTerminacionAntecedente().format(DATE_FORMATTER)).append("|");
        // IMPORTANTE: noCedula es el último campo, NO debe tener pipe después
        cadena.append(titulo.getNoCedula() != null ? titulo.getNoCedula() : "");

        // Fin de cadena original (doble pipe)
        cadena.append("||");

        return cadena.toString();
    }

    /**
     * Escapa caracteres especiales en XML según estándar SEP.
     * Sección 4.1 del estándar oficial.
     *
     * @param texto Texto a escapar
     * @return Texto con caracteres escapados
     */
    public String escaparXml(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }

        return texto
                .replace("&", "&amp;")    // & primero para no duplicar
                .replace("\"", "&quot;")   // "
                .replace("'", "&apos;")    // '
                .replace("<", "&lt;")      // <
                .replace(">", "&gt;");     // >
    }

    /**
     * Valida el XML generado contra el esquema XSD oficial.
     *
     * @param xmlContent Contenido XML a validar
     * @return true si es válido, false en caso contrario
     */
    public boolean validarContraXSD(String xmlContent) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            File xsdFile = new File("src/main/resources/xsd/TituloElectronico.xsd");

            if (!xsdFile.exists()) {
                log.warn("Archivo XSD no encontrado: {}", xsdFile.getAbsolutePath());
                return false;
            }

            Schema schema = factory.newSchema(xsdFile);
            Validator validator = schema.newValidator();

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new org.xml.sax.InputSource(new StringReader(xmlContent)));

            validator.validate(new DOMSource(doc));

            log.info("XML validado correctamente contra XSD");
            return true;

        } catch (SAXException | IOException | ParserConfigurationException e) {
            log.error("Error al validar XML contra XSD: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Formatea el XML con indentación para mejor legibilidad.
     *
     * @param xmlContent XML a formatear
     * @return XML formateado
     */
    public String formatearXml(String xmlContent) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new org.xml.sax.InputSource(new StringReader(xmlContent)));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));

            return writer.toString();

        } catch (Exception e) {
            log.error("Error al formatear XML: {}", e.getMessage());
            return xmlContent;
        }
    }

    /**
     * Valida que el sello en el XML corresponda a la cadena original generada.
     * Este método verifica la integridad criptográfica del documento.
     *
     * @param cadenaOriginal Cadena original generada
     * @param selloGenerado Sello digital que se agregó al XML
     * @param certificadoData Bytes del certificado .cer
     * @param firmaDigitalService Servicio de firma digital
     * @return Resultado de la validación con detalles
     */
    public ResultadoValidacionSello validarSelloXml(String cadenaOriginal,
                                                     String selloGenerado,
                                                     byte[] certificadoData,
                                                     FirmaDigitalService firmaDigitalService) {
        try {
            log.info("=== INICIANDO VALIDACIÓN DE SELLO ===");

            // 1. Cargar certificado
            java.security.cert.X509Certificate certificado =
                firmaDigitalService.cargarCertificadoDesdeBytes(certificadoData);

            log.info("Certificado cargado: {}", certificado.getSubjectX500Principal());

            // 2. Validar el sello contra la cadena original
            boolean selloValido = firmaDigitalService.validarSello(
                selloGenerado,
                cadenaOriginal,
                certificado
            );

            // 3. Obtener información de debug
            String infoDebug = firmaDigitalService.obtenerInfoDebugSello(
                cadenaOriginal,
                selloGenerado
            );

            log.info(infoDebug);

            // 4. Crear resultado
            ResultadoValidacionSello resultado = new ResultadoValidacionSello();
            resultado.setValido(selloValido);
            resultado.setCadenaOriginal(cadenaOriginal);
            resultado.setSelloGenerado(selloGenerado);
            resultado.setLongitudCadena(cadenaOriginal.length());
            resultado.setLongitudSello(selloGenerado.length());
            resultado.setInfoDebug(infoDebug);

            if (selloValido) {
                resultado.setMensaje("✓ SELLO VÁLIDO: El sello corresponde correctamente a los datos del XML");
                log.info("✓✓✓ VALIDACIÓN EXITOSA ✓✓✓");
            } else {
                resultado.setMensaje("✗ SELLO INVÁLIDO: El sello NO corresponde a los datos del XML");
                log.error("✗✗✗ VALIDACIÓN FALLIDA ✗✗✗");
            }

            return resultado;

        } catch (Exception e) {
            log.error("Error al validar sello: {}", e.getMessage(), e);

            ResultadoValidacionSello resultado = new ResultadoValidacionSello();
            resultado.setValido(false);
            resultado.setMensaje("Error en validación: " + e.getMessage());
            resultado.setCadenaOriginal(cadenaOriginal);
            resultado.setSelloGenerado(selloGenerado);

            return resultado;
        }
    }

    /**
     * Clase interna para encapsular el resultado de la validación del sello.
     */
    public static class ResultadoValidacionSello {
        private boolean valido;
        private String mensaje;
        private String cadenaOriginal;
        private String selloGenerado;
        private int longitudCadena;
        private int longitudSello;
        private String infoDebug;

        // Getters y Setters
        public boolean isValido() { return valido; }
        public void setValido(boolean valido) { this.valido = valido; }

        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }

        public String getCadenaOriginal() { return cadenaOriginal; }
        public void setCadenaOriginal(String cadenaOriginal) { this.cadenaOriginal = cadenaOriginal; }

        public String getSelloGenerado() { return selloGenerado; }
        public void setSelloGenerado(String selloGenerado) { this.selloGenerado = selloGenerado; }

        public int getLongitudCadena() { return longitudCadena; }
        public void setLongitudCadena(int longitudCadena) { this.longitudCadena = longitudCadena; }

        public int getLongitudSello() { return longitudSello; }
        public void setLongitudSello(int longitudSello) { this.longitudSello = longitudSello; }

        public String getInfoDebug() { return infoDebug; }
        public void setInfoDebug(String infoDebug) { this.infoDebug = infoDebug; }

        @Override
        public String toString() {
            return String.format(
                "Validación: %s\nMensaje: %s\nCadena: %d caracteres\nSello: %d caracteres",
                valido ? "VÁLIDA" : "INVÁLIDA",
                mensaje,
                longitudCadena,
                longitudSello
            );
        }
    }
}
