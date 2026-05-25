package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grupos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"calificaciones", "hibernateLazyInitializer", "handler"})
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre; // Ejemplo: "3A", "1B"

    /**
     * Nivel del plan al que pertenece el grupo (1, 2, … N), alineado con {@link Periodo#getNumero()}.
     * En modo básico (grupo por programa) es obligatorio al crear; filtra inscripción y horarios.
     */
    @Column(name = "numero_periodo")
    private Integer numeroPeriodo;

    @ManyToOne
    @JoinColumn(name = "periodo_academico_id")
    @JsonIgnoreProperties({"fechaCreacion", "fechaActualizacion", "hibernateLazyInitializer", "handler"})
    private PeriodoAcademico periodoAcademico;

    @Getter(AccessLevel.NONE)
    @Column(name = "ciclo_escolar")
    private String cicloEscolar;  // Sincronizado desde periodoAcademico.codigo para compatibilidad

    public String getCicloEscolar() {
        return periodoAcademico != null ? periodoAcademico.getCodigo() : cicloEscolar;
    }

    @Enumerated(EnumType.STRING)
    private EstatusGrupo estatus = EstatusGrupo.ACTIVO;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "programa_id")
    @JsonIgnoreProperties({"asignaturas", "alumnos", "periodos", "hibernateLazyInitializer", "handler"})
    private ProgramaEducativo programa; // modo básico (sin asignatura)

    @ManyToOne
    @JoinColumn(name = "asignatura_id")
    private Asignatura asignatura; // modo avanzado

    @ManyToOne
    @JoinColumn(name = "maestro_id")
    private Maestro maestro;

    @ManyToMany
    @JoinTable(
        name = "grupo_alumno",
        joinColumns = @JoinColumn(name = "grupo_id"),
        inverseJoinColumns = @JoinColumn(name = "alumno_id")
    )
    @JsonIgnoreProperties({"calificaciones", "observaciones_list", "documentos", "solicitudes", "programa", "usuario", "hibernateLazyInitializer", "handler"})
    private List<Alumno> alumnos = new ArrayList<>();

    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL)
    private List<Calificacion> calificaciones = new ArrayList<>();

    public enum EstatusGrupo {
        ACTIVO,
        INACTIVO,
        FINALIZADO,
        CANCELADO
    }

    // Helper methods
    public String getNombreCompleto() {
        return nombre + " - " + (asignatura != null ? asignatura.getNombre() : (programa != null ? programa.getNombre() : ""));
    }

    @PrePersist
    @PreUpdate
    private void sincronizarCicloEscolar() {
        if (periodoAcademico != null) {
            cicloEscolar = periodoAcademico.getCodigo();
        } else {
            cicloEscolar = null;
        }
    }
}
