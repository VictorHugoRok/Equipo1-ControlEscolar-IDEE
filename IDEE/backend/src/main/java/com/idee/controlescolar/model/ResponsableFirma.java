package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entidad que representa un responsable autorizado
 * para firmar títulos profesionales electrónicos.
 * Puede ser Director, Secretario Académico, etc.
 */
@Entity
@Table(name = "responsables_firma")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ResponsableFirma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre del responsable
     */
    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * Primer apellido del responsable
     */
    @Column(name = "primer_apellido", nullable = false, length = 100)
    private String primerApellido;

    /**
     * Segundo apellido del responsable
     */
    @Column(name = "segundo_apellido", length = 100)
    private String segundoApellido;

    /**
     * CURP del responsable (único)
     */
    @Column(nullable = false, unique = true, length = 18)
    private String curp;

    /**
     * ID del cargo según catálogo SEP
     */
    @Column(name = "id_cargo", nullable = false, length = 10)
    private String idCargo;

    /**
     * Nombre del cargo (ej: Director General, Secretario Académico)
     */
    @Column(nullable = false, length = 150)
    private String cargo;

    /**
     * Abreviatura del título académico (Dr., Mtro., Lic., etc.)
     */
    @Column(name = "abr_titulo", length = 20)
    private String abrTitulo;

    /**
     * Certificado digital del responsable en Base64
     */
    @Column(name = "certificado_responsable", columnDefinition = "TEXT")
    private String certificadoResponsable;

    /**
     * Número del certificado del responsable
     */
    @Column(name = "no_certificado_responsable", length = 50)
    private String noCertificadoResponsable;

    /**
     * Indica si el responsable está activo
     */
    @Column(nullable = false)
    private Boolean activo = true;

    /**
     * Orden en que aparece la firma en el XML
     * 1 = primera firma, 2 = segunda firma, etc.
     */
    @Column(name = "orden_firma", nullable = false)
    private Integer ordenFirma = 1;

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
     * Obtiene el nombre completo del responsable
     */
    @Transient
    public String getNombreCompleto() {
        StringBuilder nombreCompleto = new StringBuilder();

        if (abrTitulo != null && !abrTitulo.isEmpty()) {
            nombreCompleto.append(abrTitulo).append(" ");
        }

        nombreCompleto.append(nombre).append(" ")
                      .append(primerApellido);

        if (segundoApellido != null && !segundoApellido.isEmpty()) {
            nombreCompleto.append(" ").append(segundoApellido);
        }

        return nombreCompleto.toString();
    }

    /**
     * Verifica si el responsable tiene certificado digital
     */
    @Transient
    public boolean tieneCertificado() {
        return certificadoResponsable != null && !certificadoResponsable.isEmpty();
    }
}
