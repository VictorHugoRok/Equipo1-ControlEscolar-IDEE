package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "horarios_bloques")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class HorarioBloque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiaSemana dia;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFin;

    /** Vigencia del bloque (para modelo modular/secuencial). Null = sin límite. */
    private LocalDate fechaInicio;

    /** Vigencia del bloque (para modelo modular/secuencial). Null = sin límite. */
    private LocalDate fechaFin;

    private String aula; // Ej: "Lab 1", "Aula 301"

    @ManyToOne
    @JoinColumn(name = "grupo_id")
    @JsonIgnoreProperties({"calificaciones", "alumnos", "asignatura", "programa", "maestro"})
    private Grupo grupoEntity;  // FK a Grupo (gestión correcta)

    @Column(name = "grupo")
    private String grupo;  // Nombre "3A" - sincronizado desde grupoEntity o texto legacy

    @ManyToOne
    @JoinColumn(name = "periodo_academico_id")
    @JsonIgnoreProperties({"fechaCreacion", "fechaActualizacion"})
    private PeriodoAcademico periodoAcademico;

    @Column(name = "ciclo_escolar")
    private String cicloEscolar;  // Sincronizado desde periodoAcademico.codigo

    @PrePersist
    @PreUpdate
    private void sincronizarCampos() {
        if (periodoAcademico != null) {
            cicloEscolar = periodoAcademico.getCodigo();
        }
        if (grupoEntity != null) {
            grupo = grupoEntity.getNombre();
        }
    }

    /** Nombre del grupo para display/API (desde FK o texto legacy). */
    public String getGrupoNombre() {
        return grupoEntity != null ? grupoEntity.getNombre() : grupo;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstatusHorario estatus = EstatusHorario.ACTIVO;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "programa_id")
    @JsonIgnoreProperties({"asignaturas", "alumnos"})
    private ProgramaEducativo programa;

    @ManyToOne
    @JoinColumn(name = "asignatura_id", nullable = false)
    @JsonIgnoreProperties({"programa", "maestros", "grupos"})
    private Asignatura asignatura;

    @ManyToOne
    @JoinColumn(name = "maestro_id")
    @JsonIgnoreProperties({"asignaturas", "horariosImpartidos", "usuario", "documentos"})
    private Maestro maestro;

    // Enums
    public enum DiaSemana {
        LUNES("Lunes"),
        MARTES("Martes"),
        MIERCOLES("Miércoles"),
        JUEVES("Jueves"),
        VIERNES("Viernes"),
        SABADO("Sábado"),
        DOMINGO("Domingo");

        private final String nombre;

        DiaSemana(String nombre) {
            this.nombre = nombre;
        }

        public String getNombre() {
            return nombre;
        }
    }

    public enum EstatusHorario {
        ACTIVO,
        CANCELADO,
        SUSPENDIDO
    }

    // Helper methods
    public String getHorarioFormateado() {
        return dia.getNombre() + " " + horaInicio + " - " + horaFin;
    }
}
