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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alumnos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Alumno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String matricula;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellidoPaterno;

    @Column(nullable = false)
    private String apellidoMaterno;

    @Column(nullable = false, unique = true)
    private String curp;

    private String correoInstitucional;

    private String correoPersonal;

    private String telefono;

    private String codigoPostal;

    @Enumerated(EnumType.STRING)
    private Sexo sexo;

    private LocalDate fechaNacimiento;

    // Contacto de emergencia
    private String nombreContactoEmergencia;

    private String telefonoContactoEmergencia;

    // Información académica
    @ManyToOne
    @JoinColumn(name = "programa_id")
    @JsonIgnoreProperties({"alumnos", "asignaturas"})
    private ProgramaEducativo programa;

    private String cicloEscolar;

    @Enumerated(EnumType.STRING)
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstatusMatricula estatusMatricula = EstatusMatricula.ACTIVA;

    private String fotoUrl;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

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

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    private List<Calificacion> calificaciones = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    private List<Observacion> observaciones_list = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    private List<DocumentoAlumno> documentos = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    private List<SolicitudConstancia> solicitudes = new ArrayList<>();

    // Enums
    public enum Sexo {
        MASCULINO, FEMENINO
    }

    public enum Turno {
        MATUTINO, VESPERTINO, MIXTO
    }

    public enum EstatusMatricula {
        ACTIVA, INACTIVA, BAJA_TEMPORAL, BAJA_DEFINITIVA, EGRESADO
    }

    // Helper methods
    public String getNombreCompleto() {
        return nombre + " " + apellidoPaterno + " " + apellidoMaterno;
    }
}
