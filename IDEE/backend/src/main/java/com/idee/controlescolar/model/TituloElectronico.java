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
 * Entidad que representa un título profesional electrónico
 * según el estándar oficial de la SEP.
 */
@Entity
@Table(name = "titulos_electronicos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TituloElectronico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Folio de control interno asignado al título electrónico.
     * Debe ser único (6-40 caracteres alfanuméricos).
     */
    @Column(name = "folio_control", nullable = false, unique = true, length = 50)
    private String folioControl;

    /**
     * Relación con el alumno al que se le expide el título
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    /**
     * Relación con el programa educativo cursado
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programa_id", nullable = false)
    private ProgramaEducativo programa;

    // ========================================
    // DATOS DE EXPEDICIÓN
    // ========================================

    /**
     * Fecha de expedición del título
     */
    @Column(name = "fecha_expedicion", nullable = false)
    private LocalDate fechaExpedicion;

    /**
     * ID de la modalidad de titulación según catálogo SEP
     */
    @Column(name = "id_modalidad_titulacion", nullable = false, length = 10)
    private String idModalidadTitulacion;

    /**
     * Descripción de la modalidad de titulación (Por Tesis, Por Examen, etc.)
     */
    @Column(name = "modalidad_titulacion", nullable = false, length = 150)
    private String modalidadTitulacion;

    /**
     * Fecha del examen profesional (si aplica)
     */
    @Column(name = "fecha_examen_profesional")
    private LocalDate fechaExamenProfesional;

    /**
     * Fecha de exención del examen profesional (si aplica)
     */
    @Column(name = "fecha_exencion_examen_profesional")
    private LocalDate fechaExencionExamenProfesional;

    // ========================================
    // SERVICIO SOCIAL
    // ========================================

    /**
     * Indica si el profesionista cumplió con el servicio social
     * 1 = cumplió, 0 = no cumplió
     */
    @Column(name = "cumplio_servicio_social", nullable = false)
    private Boolean cumplioServicioSocial = true;

    /**
     * ID del fundamento legal del servicio social según catálogo SEP
     */
    @Column(name = "id_fundamento_legal_servicio_social", length = 10)
    private String idFundamentoLegalServicioSocial;

    /**
     * Descripción del fundamento legal del servicio social
     */
    @Column(name = "fundamento_legal_servicio_social")
    private String fundamentoLegalServicioSocial;

    // ========================================
    // ANTECEDENTE (Estudios previos)
    // ========================================

    /**
     * Nombre de la institución de procedencia de los estudios previos
     */
    @Column(name = "institucion_procedencia")
    private String institucionProcedencia;

    /**
     * ID del tipo de estudio antecedente según catálogo SEP
     */
    @Column(name = "id_tipo_estudio_antecedente", length = 10)
    private String idTipoEstudioAntecedente;

    /**
     * Descripción del tipo de estudio antecedente
     */
    @Column(name = "tipo_estudio_antecedente", length = 150)
    private String tipoEstudioAntecedente;

    /**
     * ID de la entidad federativa del antecedente según catálogo SEP
     */
    @Column(name = "id_entidad_federativa_antecedente", length = 10)
    private String idEntidadFederativaAntecedente;

    /**
     * Nombre de la entidad federativa del antecedente
     */
    @Column(name = "entidad_federativa_antecedente", length = 100)
    private String entidadFederativaAntecedente;

    /**
     * Fecha de inicio de los estudios de antecedente
     */
    @Column(name = "fecha_inicio_antecedente")
    private LocalDate fechaInicioAntecedente;

    /**
     * Fecha de terminación de los estudios de antecedente
     */
    @Column(name = "fecha_terminacion_antecedente")
    private LocalDate fechaTerminacionAntecedente;

    /**
     * Número de cédula profesional del antecedente (7-8 caracteres)
     */
    @Column(name = "no_cedula", length = 50)
    private String noCedula;

    // ========================================
    // ARCHIVOS GENERADOS
    // ========================================

    /**
     * Contenido del XML generado según estándar SEP
     */
    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    /**
     * Ruta del archivo XML almacenado
     */
    @Column(name = "xml_path", length = 500)
    private String xmlPath;

    /**
     * Sello digital generado con certificado SAT (Base64)
     */
    @Column(name = "sello_sat", columnDefinition = "TEXT")
    private String selloSat;

    /**
     * Cadena original usada para generar el sello digital
     */
    @Column(name = "cadena_original", columnDefinition = "TEXT")
    private String cadenaOriginal;

    // ========================================
    // ESTADO Y OBSERVACIONES
    // ========================================

    /**
     * Estado del título electrónico
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstatusTitulo estatus = EstatusTitulo.GENERADO;

    /**
     * Observaciones adicionales sobre el título
     */
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // ========================================
    // AUDITORÍA
    // ========================================

    /**
     * Fecha de creación del registro
     */
    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Fecha de última actualización
     */
    @LastModifiedDate
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    /**
     * Usuario que creó el título
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_creacion_id")
    private Usuario usuarioCreacion;

    // ========================================
    // MÉTODOS DE UTILIDAD
    // ========================================

    /**
     * Verifica si el título está completo (tiene XML y sello)
     */
    @Transient
    public boolean estaCompleto() {
        return xmlContent != null && !xmlContent.isEmpty() &&
               selloSat != null && !selloSat.isEmpty();
    }

    /**
     * Verifica si el título fue firmado digitalmente
     */
    @Transient
    public boolean estaFirmado() {
        return estatus == EstatusTitulo.FIRMADO ||
               estatus == EstatusTitulo.ENVIADO_SEP ||
               estatus == EstatusTitulo.VALIDADO_SEP ||
               estatus == EstatusTitulo.ENTREGADO;
    }

    /**
     * Verifica si el título puede ser modificado
     */
    @Transient
    public boolean puedeModificarse() {
        return estatus == EstatusTitulo.GENERADO;
    }

    /**
     * Obtiene el nombre completo del profesionista
     */
    @Transient
    public String getNombreCompletoProfesionista() {
        if (alumno == null) return "";

        StringBuilder nombre = new StringBuilder();
        nombre.append(alumno.getNombre()).append(" ")
              .append(alumno.getApellidoPaterno());

        if (alumno.getApellidoMaterno() != null && !alumno.getApellidoMaterno().isEmpty()) {
            nombre.append(" ").append(alumno.getApellidoMaterno());
        }

        return nombre.toString();
    }

    /**
     * Verifica si requiere fecha de examen profesional
     */
    @Transient
    public boolean requiereFechaExamen() {
        // Por Tesis o Por Examen normalmente requieren fecha de examen
        return idModalidadTitulacion != null &&
               (idModalidadTitulacion.equals("1") || idModalidadTitulacion.equals("2"));
    }
}
