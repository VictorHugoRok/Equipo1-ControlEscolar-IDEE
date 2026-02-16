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
@Table(name = "personal")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Personal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String curp;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellidoPaterno;

    @Column(nullable = false)
    private String apellidoMaterno;

    private String etiqueta; // Dr., Lic., etc.

    @Column(nullable = false)
    private String correoInstitucional;

    private String correoPersonal;

    private String telefono;

    private String codigoPostal;

    // Información académica
    @Enumerated(EnumType.STRING)
    private GradoAcademico gradoAcademico;

    private String cedulaProfesional;

    // Información laboral
    @Column(nullable = false)
    private String puesto;

    private String departamento;

    // Información fiscal
    private String rfc;

    private String regimenFiscal;

    private LocalDate fechaAlta;

    @Column(nullable = false)
    private Boolean activo = true;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // Contacto de emergencia
    private String nombreContactoEmergencia;

    private String telefonoContactoEmergencia;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @OneToOne
    @JoinColumn(name = "usuario_id")
    @JsonIgnoreProperties({"alumno", "maestro", "personal"})
    private Usuario usuario;

    // Enum
    public enum GradoAcademico {
        LICENCIATURA,
        ESPECIALIDAD,
        MAESTRIA,
        DOCTORADO
    }

    // Helper methods
    public String getNombreCompleto() {
        String etiq = etiqueta != null ? etiqueta + " " : "";
        return etiq + nombre + " " + apellidoPaterno + " " + apellidoMaterno;
    }
}
