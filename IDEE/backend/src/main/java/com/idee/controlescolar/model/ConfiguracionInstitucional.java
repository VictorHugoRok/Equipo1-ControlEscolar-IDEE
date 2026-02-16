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
 * Entidad que representa la configuración institucional
 * para la emisión de títulos profesionales electrónicos.
 * Solo puede existir una configuración activa a la vez.
 */
@Entity
@Table(name = "configuracion_institucional")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ConfiguracionInstitucional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Clave de la institución según catálogo SEP
     */
    @Column(name = "cve_institucion", nullable = false, length = 50)
    private String cveInstitucion;

    /**
     * Nombre completo de la institución
     */
    @Column(name = "nombre_institucion", nullable = false)
    private String nombreInstitucion;

    /**
     * ID de la entidad federativa según catálogo SEP
     */
    @Column(name = "id_entidad_federativa", nullable = false, length = 10)
    private String idEntidadFederativa;

    /**
     * Nombre de la entidad federativa
     */
    @Column(name = "entidad_federativa", nullable = false, length = 100)
    private String entidadFederativa;

    /**
     * Ruta al archivo .cer del certificado SAT (DEPRECATED - usar certificadoData)
     */
    @Column(name = "certificado_path", length = 500)
    private String certificadoPath;

    /**
     * Ruta al archivo .key de la llave privada SAT (DEPRECATED - usar llavePrivadaData)
     */
    @Column(name = "llave_privada_path", length = 500)
    private String llavePrivadaPath;

    /**
     * Contenido binario del archivo .cer del certificado SAT
     */
    @Lob
    @Column(name = "certificado_data")
    private byte[] certificadoData;

    /**
     * Nombre original del archivo .cer
     */
    @Column(name = "certificado_filename", length = 255)
    private String certificadoFilename;

    /**
     * Contenido binario del archivo .key de la llave privada SAT
     */
    @Lob
    @Column(name = "llave_privada_data")
    private byte[] llavePrivadaData;

    /**
     * Nombre original del archivo .key
     */
    @Column(name = "llave_privada_filename", length = 255)
    private String llavePrivadaFilename;

    /**
     * Contraseña de la llave privada (encriptada)
     */
    @Column(name = "password_llave_privada")
    private String passwordLlavePrivada;

    /**
     * Número del certificado SAT
     */
    @Column(name = "no_certificado_sat", length = 50)
    private String noCertificadoSat;

    /**
     * Indica si esta configuración está activa.
     * Solo puede haber una configuración activa a la vez.
     */
    @Column(nullable = false)
    private Boolean activo = true;

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
     * Verifica si la configuración tiene certificados configurados
     * (ya sea por ruta o por datos binarios)
     */
    @Transient
    public boolean tieneCertificados() {
        boolean tieneArchivos = (certificadoData != null && certificadoData.length > 0 &&
                                llavePrivadaData != null && llavePrivadaData.length > 0) ||
                               (certificadoPath != null && !certificadoPath.isEmpty() &&
                                llavePrivadaPath != null && !llavePrivadaPath.isEmpty());

        return tieneArchivos && passwordLlavePrivada != null && !passwordLlavePrivada.isEmpty();
    }

    /**
     * Obtiene el nombre completo para el XML
     */
    @Transient
    public String getNombreCompletoInstitucion() {
        return nombreInstitucion;
    }
}
