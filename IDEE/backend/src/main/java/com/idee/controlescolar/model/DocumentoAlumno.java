package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos_alumno")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class DocumentoAlumno {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumento tipoDocumento;

    /**
     * 0 = documento único por tipo; 1–4 = posición de {@link TipoDocumento#TITULO_CEDULA}.
     */
    @Column(nullable = false)
    private Integer docSlot = 0;

    @Column(length = 512)
    private String etiquetaDocumento;

    @Column(length = 128)
    private String numeroCedula;

    @Column(nullable = false)
    private Boolean entregado = false;

    private LocalDate fechaRecepcion;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    private String archivoUrl;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    @ManyToOne
    @JoinColumn(name = "alumno_id", nullable = false)
    @JsonIgnore
    private Alumno alumno;

    /**
     * Procedencia de la última carga o actualización del archivo en el expediente (auditoría).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origen_ultima_carga", length = 40)
    private OrigenExpediente origenUltimaCarga;

    @Column(name = "cargado_por_usuario_id")
    private Long cargadoPorUsuarioId;

    public enum OrigenExpediente {
        PORTAL_ALUMNO,
        EXPEDIENTE_STAFF,
        CARGA_ADMINISTRATIVA,
        REGISTRO_ALTA_ALUMNO
    }

    public enum TipoDocumento {
        ACTA_NACIMIENTO("Acta de Nacimiento"),
        CERTIFICADO_BACHILLERATO("Certificado de Bachillerato"),
        CURP("CURP"),
        INE("INE/IFE"),
        FOTOGRAFIAS("Fotografías"),
        COMPROBANTE_DOMICILIO("Comprobante de Domicilio"),
        TITULO_PROFESIONAL("Título Profesional"),
        CEDULA_PROFESIONAL("Cédula Profesional"),
        CONSTANCIA_ESTUDIOS("Certificado de estudios"),
        CONSTANCIA_SITUACION_FISCAL("Constancia de situación fiscal"),
        /** Hasta 4 registros por alumno (doc_slot 1–4); metadatos en la fila. */
        TITULO_CEDULA("Título / Cédula"),
        OTRO("Otro");

        private final String descripcion;

        TipoDocumento(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }
}
