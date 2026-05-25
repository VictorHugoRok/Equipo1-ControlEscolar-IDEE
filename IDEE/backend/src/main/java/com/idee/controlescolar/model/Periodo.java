package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodo del plan de estudios (ej. Semestre 1, 2, 3... 9).
 * Los periodos son fijos: una vez definidos para un programa, no cambian.
 * Cada periodo agrupa las asignaturas y actividades que el alumno cursa en ese nivel.
 */
@Entity
@Table(name = "periodos_plan", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"programa_id", "numero"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Periodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Número ordinal del periodo (1, 2, 3... N).
     * Debe ser único dentro del programa.
     */
    @Column(nullable = false)
    private Integer numero;

    /**
     * Nombre opcional (ej. "Primer Semestre", "Sexto Semestre").
     * Si es null, se usa "Periodo {numero}".
     */
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "programa_id", nullable = false)
    @JsonIgnoreProperties({"asignaturas", "alumnos", "periodos"})
    private ProgramaEducativo programa;

    @OneToMany(mappedBy = "periodo", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<Asignatura> asignaturas = new ArrayList<>();

    public String getNombreDisplay() {
        return nombre != null && !nombre.isBlank() ? nombre : "Periodo " + numero;
    }
}
