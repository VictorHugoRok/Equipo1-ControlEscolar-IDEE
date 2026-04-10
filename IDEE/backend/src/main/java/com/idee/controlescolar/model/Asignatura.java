package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "asignaturas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Asignatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String clave;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    private TipoAsignatura tipo;

    private Integer periodo; // Semestre/trimestre/cuatrimestre

    private Integer creditos;

    private Integer horasAula;

    private Integer horasPractica;

    private Integer horasIndependientes;

    @Enumerated(EnumType.STRING)
    private EstatusAsignatura estatus = EstatusAsignatura.ACTIVA;

    @ManyToOne
    @JoinColumn(name = "programa_id")
    @JsonIgnoreProperties({"asignaturas", "alumnos"})
    private ProgramaEducativo programa;

    @ManyToMany
    @JoinTable(
        name = "asignatura_maestro",
        joinColumns = @JoinColumn(name = "asignatura_id"),
        inverseJoinColumns = @JoinColumn(name = "maestro_id")
    )
    private List<Maestro> maestros = new ArrayList<>();

    @OneToMany(mappedBy = "asignatura")
    private List<Grupo> grupos = new ArrayList<>();

    public enum TipoAsignatura {
        OBLIGATORIA,
        OPTATIVA,
        LIBRE,
        EXTRACURRICULAR
    }

    public enum EstatusAsignatura {
        ACTIVA,
        INACTIVA
    }
}
