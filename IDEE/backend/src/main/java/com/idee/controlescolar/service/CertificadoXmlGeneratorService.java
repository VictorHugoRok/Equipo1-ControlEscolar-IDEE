package com.idee.controlescolar.service;

import com.idee.controlescolar.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.InputSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.XMLConstants;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
/**
 * Genera el XML del Documento Electrónico de Certificación (DEC)
 * según estándar SEP (namespace https://www.siged.sep.gob.mx/certificados/).
 * Elemento raíz: Dec.
 */
@Service
@Slf4j
public class CertificadoXmlGeneratorService {

    private static final String NAMESPACE = "https://www.siged.sep.gob.mx/certificados/";
    // Versión del estándar DEC según XSD (atributo version del elemento raíz Dec)
    private static final String VERSION = "3.0";
    private static final int TIPO_CERTIFICADO_IPES = 5;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00");
    private static final DateTimeFormatter DATE_TIME_EXPEDICION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Item de asignatura con calificación para el nodo Asignaturas del DEC.
     */
    public static class AsignaturaItem {
        public String idAsignatura;
        public String claveAsignatura;
        public String nombre;
        public String ciclo;
        public String calificacion;
        /** Número de periodo del plan (1,2,3...) para ordenar las asignaturas. */
        public Integer periodoNumero;
        public Integer idObservaciones;
        public String observaciones;
        public String idTipoAsignatura;
        public String tipoAsignatura;
        public String creditos;
    }

    /**
     * Genera el XML completo del DEC (Documento Electrónico de Certificación).
     *
     * @param cert        Certificado (folio, fechas, tipo)
     * @param alumno      Alumno
     * @param programa    Programa educativo
     * @param config      Configuración institucional
     * @param responsable Responsable de firma (uno; se usa el primero si hay lista)
     * @param asignaturas Lista de asignaturas con calificación
     * @param total       Total de asignaturas del plan
     * @param asignadas   Total asignaturas acreditadas en este certificado
     * @param promedio    Promedio (string)
     * @param totalCreditos   Total créditos del plan
     * @param creditosObtenidos Créditos obtenidos
     * @param numeroCiclos     Número de ciclos
     * @param sello       Sello en Base64 (o "" / "PENDIENTE_SELLO" para generar cadena después)
     * @param certificadoResponsableBase64 Certificado del responsable en Base64
     * @param noCertificadoResponsable     Número de certificado del responsable
     * @param idNombreInstitucionOverride  Si se proporciona (ej. desde plantel.idPlantel), se usa como idNombreInstitucion; si no, se usa config.getCveInstitucion()
     * @param nombrePlantel                Nombre del plantel emisor (para nombreInstitucion en Ipes)
     * @param idCampusOverride             Si se proporciona (ej. desde plantel.claveDgp), se usa como idCampus; si no, se usa config.getIdCampus()
     * @param campusOverride               Si se proporciona (ej. desde plantel.campus), se usa como campus; si no, se usa config.getCampus()
     */
    public String generarXmlDec(
            CertificadoElectronico cert,
            Alumno alumno,
            ProgramaEducativo programa,
            ConfiguracionInstitucional config,
            ResponsableFirma responsable,
            List<AsignaturaItem> asignaturas,
            int total,
            int asignadas,
            String promedio,
            String totalCreditos,
            String creditosObtenidos,
            int numeroCiclos,
            String sello,
            String certificadoResponsableBase64,
            String noCertificadoResponsable,
            String idNombreInstitucionOverride,
            String nombrePlantel,
            String idCampusOverride,
            String campusOverride) {

        if (responsable == null) {
            throw new IllegalArgumentException("Se requiere al menos un responsable de firma");
        }
        if (config == null) {
            throw new IllegalArgumentException("Se requiere configuración institucional activa");
        }

        String folio = cert.getFolioControl() != null ? cert.getFolioControl() : "";
        String selloVal = (sello != null && !sello.isEmpty()) ? sello : "PENDIENTE_SELLO";
        String certResp = certificadoResponsableBase64 != null ? certificadoResponsableBase64 : "";
        String noCert = noCertificadoResponsable != null ? noCertificadoResponsable : "";
        String idNombreInstitucion = (idNombreInstitucionOverride != null && !idNombreInstitucionOverride.isBlank())
                ? idNombreInstitucionOverride : (config.getCveInstitucion() != null ? config.getCveInstitucion() : "");

        try {
            // Generar como texto para garantizar el ORDEN de ATRIBUTOS (DOM no lo garantiza)
            String idCampus = (idCampusOverride != null && !idCampusOverride.isBlank())
                    ? idCampusOverride
                    : ((config.getIdCampus() != null && !config.getIdCampus().isBlank()) ? config.getIdCampus() : "01");
            String campus = (campusOverride != null && !campusOverride.isBlank())
                    ? campusOverride
                    : ((config.getCampus() != null && !config.getCampus().isBlank()) ? config.getCampus() : "Principal");
            String nomInst = (nombrePlantel != null && !nombrePlantel.isBlank())
                    ? nombrePlantel : config.getNombreInstitucion();

            String rvoeNum = programa.getRvoe() != null ? programa.getRvoe() : "";
            String rvoeFecha = programa.getFechaRvoe() != null ? programa.getFechaRvoe().format(DATE_TIME_FORMAT) : "";

            String idTipoPeriodo = programa.getTipoPeriodo() != null ? programa.getTipoPeriodo().getIdOficial() : "91";
            String tipoPeriodo = programa.getTipoPeriodo() != null ? programa.getTipoPeriodo().name() : "";
            String idNivel = programa.getTipoPrograma() != null ? programa.getTipoPrograma().getIdOficial() : "81";
            String nivelEstudios = programa.getTipoPrograma() != null ? programa.getTipoPrograma().name() : "";
            String idCarrera = programa.getIdPrograma() != null && !programa.getIdPrograma().isBlank() ? programa.getIdPrograma() : "";
            String clavePlan = (programa.getPlanEstudio() != null && !programa.getPlanEstudio().isBlank())
                    ? programa.getPlanEstudio().trim() : "2023";

            String idGenero = alumno.getSexo() != null ? alumno.getSexo().getIdOficial() : "251";
            String fechaNac = alumno.getFechaNacimiento() != null ? alumno.getFechaNacimiento().format(DATE_TIME_FORMAT) : "";

            // "fecha" en el XML debe conservar el formato con hora, pero sin depender del instante real de generación.
            // Se fija a 00:00:00 (como fechaExpedicion/fechaNacimiento usan 00:00:00 en su formatter).
            LocalDate fechaExpedicionBase = cert.getFechaExpedicion() != null ? cert.getFechaExpedicion() : LocalDate.now();
            LocalDateTime fechaHoraExpedicion = LocalDateTime.of(fechaExpedicionBase, LocalTime.MIDNIGHT);
            String fechaExp = fechaHoraExpedicion.format(DATE_TIME_EXPEDICION_FORMAT);

            StringBuilder sb = new StringBuilder(16_384);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<Dec");
            // Orden solicitado: xmlns, version, tipoCertificado, folioControl, sello, certificadoResponsable, noCertificadoResponsable
            sb.append(" xmlns=\"").append(escaparXml(NAMESPACE)).append("\"");
            sb.append(" version=\"").append(escaparXml(VERSION)).append("\"");
            sb.append(" tipoCertificado=\"").append(escaparXml(String.valueOf(TIPO_CERTIFICADO_IPES))).append("\"");
            sb.append(" folioControl=\"").append(escaparXml(folio)).append("\"");
            sb.append(" sello=\"").append(escaparXml(selloVal)).append("\"");
            sb.append(" certificadoResponsable=\"").append(escaparXml(certResp)).append("\"");
            sb.append(" noCertificadoResponsable=\"").append(escaparXml(noCert)).append("\"");
            sb.append(">");

            // ServicioFirmante: idEntidad
            sb.append("<ServicioFirmante");
            sb.append(" idEntidad=\"").append(escaparXml(idNombreInstitucion)).append("\"");
            sb.append("/>");

            // Ipes: idNombreInstitucion, nombreInstitucion, idCampus, campus, idEntidadFederativa, entidadFederativa
            sb.append("<Ipes");
            sb.append(" idNombreInstitucion=\"").append(escaparXml(idNombreInstitucion)).append("\"");
            sb.append(" nombreInstitucion=\"").append(escaparXml(textoCapturado(nomInst))).append("\"");
            sb.append(" idCampus=\"").append(escaparXml(idCampus)).append("\"");
            sb.append(" campus=\"").append(escaparXml(textoCapturado(campus))).append("\"");
            sb.append(" idEntidadFederativa=\"").append(escaparXml(config.getIdEntidadFederativa())).append("\"");
            sb.append(" entidadFederativa=\"").append(escaparXml(normalizarEntidadFederativaXml(config.getEntidadFederativa()))).append("\"");
            sb.append(">");

            // Responsable: curp, nombre, primerApellido, segundoApellido, idCargo, cargo
            sb.append("<Responsable");
            sb.append(" curp=\"").append(escaparXml(responsable.getCurp())).append("\"");
            sb.append(" nombre=\"").append(escaparXml(textoCapturado(responsable.getNombre()))).append("\"");
            sb.append(" primerApellido=\"").append(escaparXml(textoCapturado(responsable.getPrimerApellido()))).append("\"");
            sb.append(" segundoApellido=\"").append(escaparXml(textoCapturado(responsable.getSegundoApellido()))).append("\"");
            sb.append(" idCargo=\"").append(escaparXml(extraerSoloIdCargo(responsable.getIdCargo()))).append("\"");
            sb.append(" cargo=\"").append(escaparXml(textoCapturado(extraerSoloTextoCargo(responsable.getCargo(), responsable.getIdCargo())))).append("\"");
            sb.append("/>");
            sb.append("</Ipes>");

            // Rvoe: numero, fechaExpedicion
            sb.append("<Rvoe");
            sb.append(" numero=\"").append(escaparXml(rvoeNum)).append("\"");
            sb.append(" fechaExpedicion=\"").append(escaparXml(rvoeFecha)).append("\"");
            sb.append("/>");

            // Carrera: idCarrera, claveCarrera, nombreCarrera, idTipoPeriodo, tipoPeriodo, clavePlan, idNivelEstudios, nivelEstudios, calificacionMinima, calificacionMaxima, calificacionMinimaAprobatoria
            sb.append("<Carrera");
            sb.append(" idCarrera=\"").append(escaparXml(idCarrera)).append("\"");
            sb.append(" claveCarrera=\"").append(escaparXml(programa.getClave())).append("\"");
            sb.append(" nombreCarrera=\"").append(escaparXml(textoCapturado(programa.getNombre()))).append("\"");
            sb.append(" idTipoPeriodo=\"").append(escaparXml(idTipoPeriodo)).append("\"");
            sb.append(" tipoPeriodo=\"").append(escaparXml(toTitleCaseEs(tipoPeriodo))).append("\"");
            sb.append(" clavePlan=\"").append(escaparXml(clavePlan)).append("\"");
            sb.append(" idNivelEstudios=\"").append(escaparXml(idNivel)).append("\"");
            sb.append(" nivelEstudios=\"").append(escaparXml(toTitleCaseEs(nivelEstudios))).append("\"");
            sb.append(" calificacionMinima=\"5\"");
            sb.append(" calificacionMaxima=\"10\"");
            sb.append(" calificacionMinimaAprobatoria=\"7.00\"");
            sb.append("/>");

            // Alumno: numeroControl, curp, nombre, primerApellido, segundoApellido, idGenero, fechaNacimiento
            sb.append("<Alumno");
            sb.append(" numeroControl=\"").append(escaparXml(alumno.getMatricula())).append("\"");
            sb.append(" curp=\"").append(escaparXml(alumno.getCurp())).append("\"");
            sb.append(" nombre=\"").append(escaparXml(textoCapturado(alumno.getNombre()))).append("\"");
            sb.append(" primerApellido=\"").append(escaparXml(textoCapturado(alumno.getApellidoPaterno()))).append("\"");
            sb.append(" segundoApellido=\"").append(escaparXml(textoCapturado(alumno.getApellidoMaterno()))).append("\"");
            sb.append(" idGenero=\"").append(escaparXml(idGenero)).append("\"");
            sb.append(" fechaNacimiento=\"").append(escaparXml(fechaNac)).append("\"");
            sb.append("/>");

            // Expedicion: idTipoCertificacion, tipoCertificacion, fecha, idLugarExpedicion, lugarExpedicion
            sb.append("<Expedicion");
            sb.append(" idTipoCertificacion=\"").append(escaparXml(cert.getIdTipoCertificado())).append("\"");
            sb.append(" tipoCertificacion=\"").append(escaparXml(textoCapturado(cert.getTipoCertificado()))).append("\"");
            sb.append(" fecha=\"").append(escaparXml(fechaExp)).append("\"");
            sb.append(" idLugarExpedicion=\"").append(escaparXml(config.getIdEntidadFederativa())).append("\"");
            sb.append(" lugarExpedicion=\"").append(escaparXml(normalizarEntidadFederativaXml(config.getEntidadFederativa()))).append("\"");
            sb.append("/>");

            // Asignaturas: total, asignadas, promedio, totalCreditos, creditosObtenidos, numeroCiclos
            sb.append("<Asignaturas");
            sb.append(" total=\"").append(escaparXml(String.valueOf(total))).append("\"");
            sb.append(" asignadas=\"").append(escaparXml(String.valueOf(asignadas))).append("\"");
            sb.append(" promedio=\"").append(escaparXml(promedio)).append("\"");
            sb.append(" totalCreditos=\"").append(escaparXml(fmtCreditos(totalCreditos))).append("\"");
            sb.append(" creditosObtenidos=\"").append(escaparXml(fmtCreditos(creditosObtenidos))).append("\"");
            sb.append(" numeroCiclos=\"").append(escaparXml(String.valueOf(numeroCiclos))).append("\"");
            sb.append(">");

            if (asignaturas != null) {
                for (AsignaturaItem a : asignaturas) {
                    // Asignatura: idAsignatura, claveAsignatura, nombre, ciclo, calificacion, idObservaciones, observaciones, idTipoAsignatura, tipoAsignatura, creditos
                    sb.append("<Asignatura");
                    sb.append(" idAsignatura=\"").append(escaparXml(a.idAsignatura)).append("\"");
                    sb.append(" claveAsignatura=\"").append(escaparXml(a.claveAsignatura)).append("\"");
                    sb.append(" nombre=\"").append(escaparXml(textoCapturado(a.nombre))).append("\"");
                    sb.append(" ciclo=\"").append(escaparXml(a.ciclo)).append("\"");
                    sb.append(" calificacion=\"").append(escaparXml(fmtCalificacion(a.calificacion))).append("\"");
                    sb.append(" idObservaciones=\"").append(escaparXml(String.valueOf(a.idObservaciones != null ? a.idObservaciones : ObservacionCalificacion.ID_DEFAULT))).append("\"");
                    String obs = (a.observaciones != null && !a.observaciones.isBlank()) ? a.observaciones : "ORDINARIO";
                    sb.append(" observaciones=\"").append(escaparXml(toTitleCaseEs(obs))).append("\"");
                    sb.append(" idTipoAsignatura=\"").append(escaparXml(a.idTipoAsignatura)).append("\"");
                    sb.append(" tipoAsignatura=\"").append(escaparXml(toTitleCaseEs(a.tipoAsignatura))).append("\"");
                    sb.append(" creditos=\"").append(escaparXml(fmtCreditos(a.creditos))).append("\"");
                    sb.append("/>");
                }
            }
            sb.append("</Asignaturas>");

            // Placeholders para timbrado SEP (implementación futura)
            sb.append("<Dreoe/>");
            sb.append("<SepIpes/>");

            sb.append("</Dec>");
            return sb.toString();
        } catch (Exception ex) {
            log.error("Error al generar XML DEC con DOM: {}", ex.getMessage(), ex);
            throw new RuntimeException("Error al generar XML del certificado: " + ex.getMessage(), ex);
        }
    }

    /**
     * Establece atributo XML. Sanitiza el valor para cadena original:
     * el carácter pipe (|) está prohibido en atributos (es carácter de control).
     */
    private static void setAttr(Element el, String name, String value) {
        if (value == null) value = "";
        value = sanitizarParaCadenaOriginal(value);
        el.setAttribute(name, value);
    }

    /**
     * Elimina o reemplaza el carácter pipe (|) en valores de atributos.
     * Según especificación SEP: ningún atributo del XML debe contener | en su valor.
     */
    private static String sanitizarParaCadenaOriginal(String valor) {
        if (valor == null || valor.isEmpty()) return valor;
        return valor.replace("|", " ");
    }

    /** Formato requerido en DEC: créditos con 2 decimales (ej. 10 -> 10.00). */
    private static String fmtCreditos(String raw) {
        if (raw == null) return "0.00";
        String s = raw.trim();
        if (s.isEmpty()) return "0.00";
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception ignored) {
            return s;
        }
    }

    /**
     * Calificaciones en DEC:
     * - Siempre con 2 decimales (ej. 7 -> 7.00, 7.2 -> 7.20, 10 -> 10.00)
     * - Sin redondear: si hay más de 2 decimales, se trunca.
     */
    private static String fmtCalificacion(String raw) {
        if (raw == null) return "0.00";
        String s = raw.trim();
        if (s.isEmpty()) return "0.00";
        try {
            BigDecimal bd = new BigDecimal(s);
            return bd.setScale(2, RoundingMode.DOWN).toPlainString();
        } catch (Exception ignored) {
            return s;
        }
    }

    private static String normalizarEntidadFederativaXml(String raw) {
        String base = toTitleCaseEs(raw);
        // Correcciones comunes con acentos / partículas
        String k = quitarAcentos(base).toUpperCase();
        return switch (k) {
            case "YUCATAN" -> "Yucatán";
            case "MICHOACAN" -> "Michoacán";
            case "NUEVO LEON" -> "Nuevo León";
            case "QUERETARO" -> "Querétaro";
            case "SAN LUIS POTOSI" -> "San Luis Potosí";
            case "CIUDAD DE MEXICO" -> "Ciudad de México";
            case "ESTADO DE MEXICO" -> "Estado de México";
            default -> base;
        };
    }

    private static String quitarAcentos(String s) {
        if (s == null) return "";
        return s.replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace("Ü", "U").replace("ü", "u");
    }

    /**
     * Extrae únicamente el número del idCargo (sin texto ni símbolos).
     * Si viene "1|DIRECTOR" retorna "1"; si viene "10" retorna "10".
     */
    private static String extraerSoloIdCargo(String idCargo) {
        if (idCargo == null || idCargo.isEmpty()) return "";
        if (idCargo.contains("|")) {
            idCargo = idCargo.split("\\|")[0];
        }
        return idCargo.replaceAll("[^0-9]", "");
    }

    /**
     * Extrae únicamente el texto del cargo (sin número ni |).
     * Si idCargo es "1|DIRECTOR" se usa la parte después de |; si cargo ya es "DIRECTOR" se usa tal cual.
     */
    private static String extraerSoloTextoCargo(String cargo, String idCargo) {
        if (idCargo != null && idCargo.contains("|")) {
            String[] parts = idCargo.split("\\|", 2);
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                return toTitleCaseEs(parts[1].trim());
            }
        }
        if (cargo != null && cargo.contains("|")) {
            String[] parts = cargo.split("\\|", 2);
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                return toTitleCaseEs(parts[1].trim());
            }
            return toTitleCaseEs(parts[0].trim());
        }
        return cargo != null ? toTitleCaseEs(cargo.trim()) : "";
    }

    private static String toTitleCaseEs(String s) {
        if (s == null) return "";
        String raw = s.trim();
        if (raw.isEmpty()) return "";
        String lower = raw.toLowerCase();
        String[] parts = lower.split("\\s+");
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (w.isEmpty()) continue;
            if (i > 0) out.append(' ');
            // partículas comunes en minúscula, salvo primera palabra
            if (i > 0 && (w.equals("de") || w.equals("del") || w.equals("la") || w.equals("las") || w.equals("los")
                    || w.equals("y") || w.equals("e") || w.equals("en") || w.equals("al"))) {
                out.append(w);
            } else {
                out.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) out.append(w.substring(1));
            }
        }
        return out.toString();
    }

    /**
     * Texto tal como se captura en el sistema (sin forzar mayúsculas).
     * El carácter | se elimina en {@link #setAttr}; CURP y claves se pasan sin pasar por aquí cuando aplica.
     */
    private static String textoCapturado(String s) {
        if (s == null || s.isEmpty()) {
            return s != null ? s : "";
        }
        return s;
    }

    /**
     * Escapa caracteres especiales para uso en atributos XML.
     * Normaliza saltos de línea, tabuladores y caracteres de control para evitar
     * el error del parser "elemento debe ir seguido de > o />".
     */
    public String escaparXml(String texto) {
        if (texto == null) return "";
        // Eliminar caracteres de control (0x00-0x1F) que invalidan atributos XML
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c >= 0x20 || c == 0x09 || c == 0x0A || c == 0x0D) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        String normalized = sb.toString()
                .replace("\r\n", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
        if (normalized.isEmpty()) return "";
        normalized = sanitizarParaCadenaOriginal(normalized);
        return normalized
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Genera la cadena original aplicando el XSLT oficial al XML del DEC.
     * Parsea el XML a DOM (namespace aware) antes de transformar para evitar errores
     * del parser con atributos que contienen saltos de línea o caracteres especiales.
     */
    public String generarCadenaOriginalConXslt(String xmlDec) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) { /* parser puede no soportar la característica */ }
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xmlDec)));

            InputStream xsltStream = new ClassPathResource("xsl/CertificadoElectronico.xslt").getInputStream();
            Source xsltSource = new StreamSource(xsltStream);
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer(xsltSource);

            Source xmlSource = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            transformer.transform(xmlSource, new StreamResult(writer));
            String cadena = writer.toString().trim();
            log.debug("Cadena original DEC generada: {} caracteres", cadena.length());

            // Validar formato según especificación: debe iniciar y terminar con ||
            if (cadena.isEmpty()) {
                throw new RuntimeException("La cadena original generada está vacía. Verifique la estructura del XML DEC.");
            }
            if (!cadena.startsWith("||") || !cadena.endsWith("||")) {
                throw new RuntimeException("La cadena original no cumple el formato: debe iniciar y terminar con ||. Generada: " + cadena.substring(0, Math.min(80, cadena.length())) + "...");
            }
            return cadena;
        } catch (Exception e) {
            log.error("Error al generar cadena original con XSLT: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar cadena original del certificado: " + e.getMessage(), e);
        }
    }

    /**
     * Genera HTML para vista previa del certificado (impresión o guardar como PDF).
     * Usa XSLT CertificadoVistaPrevia.xslt que transforma el XML Dec a HTML con formato de certificado.
     * @param claveCct Clave CCT del plantel emisor (no está en el XSD oficial SEP; se pasa como parámetro al XSLT).
     */
    public String generarHtmlVistaPrevia(String xmlDec, String claveCct) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) { }
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xmlDec)));

            InputStream xsltStream = new ClassPathResource("xsl/CertificadoVistaPrevia.xslt").getInputStream();
            Source xsltSource = new StreamSource(xsltStream);
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer(xsltSource);
            transformer.setParameter("claveCct", claveCct != null ? claveCct : "");

            Source xmlSource = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            transformer.transform(xmlSource, new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("Error al generar HTML vista previa: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar vista previa del certificado: " + e.getMessage(), e);
        }
    }

    /**
     * Resultado de la validación XSD con lista de errores específicos.
     */
    public static class ResultadoValidacionXsd {
        public final boolean valido;
        public final List<String> errores;

        public ResultadoValidacionXsd(boolean valido, List<String> errores) {
            this.valido = valido;
            this.errores = errores != null ? errores : List.of();
        }
    }

    /**
     * Valida el XML del DEC contra el esquema XSD (classpath).
     * Devuelve el resultado y la lista de errores específicos cuando falla.
     */
    public ResultadoValidacionXsd validarContraXSDConErrores(String xmlContent) {
        List<String> errores = new ArrayList<>();
        try {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            InputStream xsdStream = new ClassPathResource("xsd/CertificadoElectronico.xsd").getInputStream();
            Schema schema = sf.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();

            ErrorHandler errorHandler = new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    errores.add("Advertencia [L" + e.getLineNumber() + ",C" + e.getColumnNumber() + "]: " + e.getMessage());
                }

                @Override
                public void error(SAXParseException e) {
                    errores.add("Error [L" + e.getLineNumber() + ",C" + e.getColumnNumber() + "]: " + e.getMessage());
                }

                @Override
                public void fatalError(SAXParseException e) {
                    errores.add("Error fatal [L" + e.getLineNumber() + ",C" + e.getColumnNumber() + "]: " + e.getMessage());
                }
            };
            validator.setErrorHandler(errorHandler);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new org.xml.sax.InputSource(new StringReader(xmlContent)));
            validator.validate(new DOMSource(doc));
            if (errores.isEmpty()) {
                log.info("XML DEC validado correctamente contra XSD");
                return new ResultadoValidacionXsd(true, List.of());
            }
            log.warn("XML DEC con advertencias XSD: {}", errores);
            return new ResultadoValidacionXsd(false, errores);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (e.getCause() != null) {
                msg = e.getCause().getMessage();
            }
            errores.add("Error de validación: " + msg);
            log.error("Error al validar XML DEC contra XSD: {}", e.getMessage());
            return new ResultadoValidacionXsd(false, errores);
        }
    }

    /**
     * Valida el XML del DEC contra el esquema XSD (classpath).
     * @deprecated Usar {@link #validarContraXSDConErrores(String)} para obtener errores detallados.
     */
    public boolean validarContraXSD(String xmlContent) {
        return validarContraXSDConErrores(xmlContent).valido;
    }

}
