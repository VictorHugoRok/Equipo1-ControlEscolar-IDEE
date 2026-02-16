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
@Table(name = "programas_educativos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProgramaEducativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String clave;

    @Column(name = "clave_dgp")
    private String claveDgp;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPrograma tipoPrograma;

    private Integer duracionPeriodos;

    @Enumerated(EnumType.STRING)
    private TipoPeriodo tipoPeriodo;

    @Enumerated(EnumType.STRING)
    private Modalidad modalidad;

    private Integer creditosTotales;

    private String rvoe;

    private LocalDate fechaRvoe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstatusPrograma estatus = EstatusPrograma.ACTIVO;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @OneToMany(mappedBy = "programa", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("programa")
    private List<Asignatura> asignaturas = new ArrayList<>();

    @OneToMany(mappedBy = "programa")
    @JsonIgnoreProperties({"programa", "calificaciones", "observaciones_list", "documentos", "solicitudes", "usuario"})
    private List<Alumno> alumnos = new ArrayList<>();

    // Enums
    public enum TipoPrograma {
        LICENCIATURA,
        MAESTRIA,
        ESPECIALIDAD,
        DOCTORADO,
        EXTRACURRICULAR
    }

    public enum TipoPeriodo {
        SEMESTRE,
        TRIMESTRE,
        CUATRIMESTRE
    }

    public enum Modalidad {
        ESCOLARIZADO,
        MIXTO,
        EN_LINEA
    }

    public enum EstatusPrograma {
        ACTIVO,
        INACTIVO
    }
}
