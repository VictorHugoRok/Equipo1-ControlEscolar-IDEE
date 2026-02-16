package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
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
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre; // Ejemplo: "3A", "1B"

    @Column(nullable = false)
    private String periodo; // Ejemplo: "2025-2"

    private String cicloEscolar; // Ejemplo: "2025-2029"

    @Enumerated(EnumType.STRING)
    private EstatusGrupo estatus = EstatusGrupo.ACTIVO;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "asignatura_id")
    private Asignatura asignatura;

    @ManyToOne
    @JoinColumn(name = "maestro_id")
    private Maestro maestro;

    @ManyToMany
    @JoinTable(
        name = "grupo_alumno",
        joinColumns = @JoinColumn(name = "grupo_id"),
        inverseJoinColumns = @JoinColumn(name = "alumno_id")
    )
    private List<Alumno> alumnos = new ArrayList<>();

    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL)
    private List<Calificacion> calificaciones = new ArrayList<>();

    public enum EstatusGrupo {
        ACTIVO,
        FINALIZADO,
        CANCELADO
    }

    // Helper methods
    public String getNombreCompleto() {
        return nombre + " - " + (asignatura != null ? asignatura.getNombre() : "");
    }
}
