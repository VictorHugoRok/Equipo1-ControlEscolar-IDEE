package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad que representa un certificado electrónico según el estándar SEP/MEC.
 * Sigue el mismo patrón que TituloElectronico: reutiliza Alumno, ProgramaEducativo,
 * y en el servicio se usan ConfiguracionInstitucional y ResponsableFirma para
 * generar el XML y la firma.
 */
@Entity
@Table(name = "certificados_electronicos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CertificadoElectronico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Folio de control interno del certificado. Debe ser único (según longitud definida por SEP/MEC).
     */
    @Column(name = "folio_control", nullable = false, unique = true, length = 50)
    private String folioControl;

    /**
     * Alumno al que se le expide el certificado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    /**
     * Programa educativo al que corresponde el certificado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programa_id", nullable = false)
    private ProgramaEducativo programa;

    /**
     * Plantel emisor del certificado. Se usa para obtener claveCct en la vista previa PDF
     * (el XSD oficial SEP no incluye claveCct en el XML).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantel_id")
    private Plantel plantel;

    // ========================================
    // DATOS DE EXPEDICIÓN (específicos del certificado MEC)
    // ========================================

    /**
     * Fecha de expedición del certificado.
     */
    @Column(name = "fecha_expedicion", nullable = false)
    private LocalDate fechaExpedicion;

    /**
     * ID del tipo de certificado según catálogo SEP (ej. parcial, total, de estudios).
     */
    @Column(name = "id_tipo_certificado", nullable = false, length = 10)
    private String idTipoCertificado;

    /**
     * Descripción del tipo de certificado.
     */
    @Column(name = "tipo_certificado", nullable = false, length = 150)
    private String tipoCertificado;

    /**
     * Periodo escolar (ej. 2025-1, 2024-2).
     */
    @Column(name = "periodo", length = 20)
    private String periodo;

    /**
     * Ciclo escolar (ej. 2024-2025).
     */
    @Column(name = "ciclo_escolar", length = 20)
    private String cicloEscolar;

    /**
     * Fecha de inicio del periodo que ampara el certificado (si aplica).
     */
    @Column(name = "fecha_inicio_periodo")
    private LocalDate fechaInicioPeriodo;

    /**
     * Fecha de fin del periodo que ampara el certificado (si aplica).
     */
    @Column(name = "fecha_fin_periodo")
    private LocalDate fechaFinPeriodo;

    // ========================================
    // ARCHIVOS GENERADOS (igual que títulos)
    // ========================================

    /**
     * Contenido del XML generado según estándar SEP/MEC.
     */
    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    /**
     * Ruta del archivo XML almacenado en disco.
     */
    @Column(name = "xml_path", length = 500)
    private String xmlPath;

    /**
     * Sello digital generado con certificado SAT (Base64).
     */
    @Column(name = "sello_sat", columnDefinition = "TEXT")
    private String selloSat;

    /**
     * Cadena original usada para generar el sello digital (según especificación MEC).
     */
    @Column(name = "cadena_original", columnDefinition = "TEXT")
    private String cadenaOriginal;

    /**
     * Indica si el XML pasó la validación contra el XSD oficial de la SEP.
     * Si false, no se debe permitir descargar ni visualizar el certificado.
     */
    @Column(name = "valido_xsd", nullable = false)
    private Boolean validoXsd = true;

    /**
     * Detalle de los errores de validación XSD cuando validoXsd es false.
     * Cada línea describe el valor o elemento que no pasó la comparación.
     */
    @Column(name = "errores_xsd", columnDefinition = "TEXT")
    private String erroresXsd;

    // ========================================
    // ESTADO Y OBSERVACIONES
    // ========================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstatusCertificado estatus = EstatusCertificado.GENERADO;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // ========================================
    // AUDITORÍA
    // ========================================

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_creacion_id")
    private Usuario usuarioCreacion;

    // ========================================
    // MÉTODOS DE UTILIDAD
    // ========================================

    /**
     * Indica si el certificado tiene XML y sello (listo para entrega).
     */
    @Transient
    public boolean estaCompleto() {
        return xmlContent != null && !xmlContent.isEmpty() &&
               selloSat != null && !selloSat.isEmpty();
    }

    /**
     * Indica si ya fue firmado digitalmente.
     */
    @Transient
    public boolean estaFirmado() {
        return estatus == EstatusCertificado.FIRMADO ||
               estatus == EstatusCertificado.ENVIADO_SEP ||
               estatus == EstatusCertificado.VALIDADO_SEP ||
               estatus == EstatusCertificado.ENTREGADO;
    }

    /**
     * Indica si aún puede modificarse (solo en estado GENERADO).
     */
    @Transient
    public boolean puedeModificarse() {
        return estatus == EstatusCertificado.GENERADO;
    }

    /**
     * Nombre completo del alumno (para reportes y XML).
     */
    @Transient
    public String getNombreCompletoAlumno() {
        if (alumno == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(alumno.getNombre()).append(" ")
          .append(alumno.getApellidoPaterno());
        if (alumno.getApellidoMaterno() != null && !alumno.getApellidoMaterno().isEmpty()) {
            sb.append(" ").append(alumno.getApellidoMaterno());
        }
        return sb.toString();
    }
}
