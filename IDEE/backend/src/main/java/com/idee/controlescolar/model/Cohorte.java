package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Table(name = "cohortes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Cohorte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Programa educativo al que pertenece la cohorte.
     * La BD requiere programa_id NOT NULL.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "programa_id", nullable = false)
    @JsonIgnoreProperties({"alumnos", "asignaturas", "periodos", "hibernateLazyInitializer", "handler"})
    private ProgramaEducativo programa;

    /**
     * ID alfanumérico único definido por usuario (ej. ORTO-2024).
     */
    @Column(name = "id_cohorte", nullable = false, unique = true, length = 60)
    private String idCohorte;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @ManyToMany(mappedBy = "cohortes")
    @JsonIgnore
    private List<Alumno> miembros = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;
}
