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
@Table(name = "maestros")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Maestro {

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

    private String etiqueta; // Dr., Mtro., etc.

    @Column(nullable = false)
    private String correoInstitucional;

    private String correoPersonal;

    private String telefono;

    private String codigoPostal;

    // Información académica
    @Enumerated(EnumType.STRING)
    private GradoAcademico gradoAcademico;

    private String cedulaProfesional;

    private String area; // Salud, Ingenierías, etc.

    // Información fiscal
    private String rfc;

    private String regimenFiscal;

    // Información laboral
    @Enumerated(EnumType.STRING)
    private TipoMaestro tipoMaestro;

    private LocalDate fechaAlta;

    @Column(nullable = false)
    private Boolean activo = true;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String observaciones;

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

    @OneToMany(mappedBy = "maestro", cascade = CascadeType.ALL)
    private List<Grupo> grupos = new ArrayList<>();

    @OneToMany(mappedBy = "maestro", cascade = CascadeType.ALL)
    private List<HorarioBloque> horariosImpartidos = new ArrayList<>();

    @ManyToMany(mappedBy = "maestros")
    private List<Asignatura> asignaturas = new ArrayList<>();

    @OneToMany(mappedBy = "maestro", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"maestro", "data"})
    private List<MaestroDocumento> documentos = new ArrayList<>();

    // Enums
    public enum GradoAcademico {
        LICENCIATURA,
        ESPECIALIDAD,
        MAESTRIA,
        DOCTORADO
    }

    public enum TipoMaestro {
        TIEMPO_COMPLETO,
        MEDIO_TIEMPO,
        POR_HORAS
    }

    // Helper methods
    public String getNombreCompleto() {
        String etiq = etiqueta != null ? etiqueta + " " : "";
        return etiq + nombre + " " + apellidoPaterno + " " + apellidoMaterno;
    }
}
