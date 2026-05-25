package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Foto de perfil del usuario (ruta en disco).
     */
    private String fotoUrl;

    /**
     * Género (catálogo SEP). Se captura en el registro general para todos los usuarios.
     * Coincide con {@link com.idee.controlescolar.model.Alumno.Sexo}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Alumno.Sexo sexo;

    /**
     * Fecha de nacimiento. Se captura en el registro general para todos los usuarios.
     */
    @Column(nullable = false)
    private LocalDate fechaNacimiento;

    // Información académica
    @Enumerated(EnumType.STRING)
    private GradoAcademico gradoAcademico;

    private String cedulaProfesional;

    // Información laboral
    @Column(nullable = false)
    private String puesto;

    private String departamento;

    /** Área académica (docencia); si aplica rol MAESTRO. */
    private String area;

    /** Tipo de contratación como docente (tiempo completo, etc.). */
    @Enumerated(EnumType.STRING)
    private Maestro.TipoMaestro tipoMaestro;

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
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario usuario;

    @OneToMany(mappedBy = "personal", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PersonalDocumento> documentos = new ArrayList<>();

    @OneToMany(mappedBy = "personal", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"personal", "data"})
    private List<PersonalCedulaProfesional> cedulasProfesionales = new ArrayList<>();

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
