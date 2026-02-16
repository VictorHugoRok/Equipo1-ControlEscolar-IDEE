package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos_alumno")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DocumentoAlumno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(nullable = false)
    private Boolean entregado = false;

    private LocalDate fechaRecepcion;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    private String archivoUrl; // URL o path del archivo

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "alumno_id", nullable = false)
    @JsonIgnoreProperties({"documentos", "calificaciones", "observaciones_list", "solicitudes", "usuario"})
    private Alumno alumno;

    // Enums
    public enum TipoDocumento {
        ACTA_NACIMIENTO("Acta de Nacimiento"),
        CERTIFICADO_BACHILLERATO("Certificado de Bachillerato"),
        CURP("CURP"),
        INE("INE/IFE"),
        FOTOGRAFIAS("Fotografías"),
        COMPROBANTE_DOMICILIO("Comprobante de Domicilio"),
        TITULO_PROFESIONAL("Título Profesional"),
        CEDULA_PROFESIONAL("Cédula Profesional"),
        CONSTANCIA_ESTUDIOS("Constancia de Estudios"),
        CONSTANCIA_SITUACION_FISCAL("Constancia de Situación Fiscal"),
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
