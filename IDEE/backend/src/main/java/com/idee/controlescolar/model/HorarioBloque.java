package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
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

    private String aula; // Ej: "Lab 1", "Aula 301"

    private String grupo; // Ej: "3A"

    private String periodo; // Ej: "2025-2"

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
    private ProgramaEducativo programa;

    @ManyToOne
    @JoinColumn(name = "asignatura_id", nullable = false)
    private Asignatura asignatura;

    @ManyToOne
    @JoinColumn(name = "maestro_id", nullable = false)
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
