package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evaluacion_docente_respuesta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluacionDocenteRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "formulario_id")
    @JsonIgnoreProperties({"preguntas"})
    private EvaluacionDocenteFormulario formulario;

    /**
     * Respondente alumno (Evaluación Docente).
     * En Evaluación Académica, este campo queda NULL y se usa {@link #evaluadorUsuario}.
     */
    @ManyToOne(optional = true)
    @JoinColumn(name = "alumno_id")
    @JsonIgnoreProperties({"documentos", "calificaciones", "observaciones_list", "solicitudes", "usuario"})
    private Alumno alumno;

    /**
     * Usuario evaluador (Secretaría Académica / Admin) para Evaluación Académica.
     * En Evaluación Docente (por alumnos), este campo queda NULL y se usa {@link #alumno}.
     */
    @ManyToOne(optional = true)
    @JoinColumn(name = "evaluador_usuario_id")
    @JsonIgnoreProperties({"alumno", "maestro", "personal", "programasAsignados", "roles"})
    private Usuario evaluadorUsuario;

    /**
     * Módulo/clase (Evaluación Docente por alumno y Autoevaluación): vigencia definida en el horario.
     */
    @ManyToOne(optional = true)
    @JoinColumn(name = "horario_bloque_id")
    @JsonIgnoreProperties({"grupoEntity", "programa", "asignatura", "maestro", "periodoAcademico"})
    private HorarioBloque horarioBloque;

    /**
     * Docente evaluado en esta respuesta (Evaluación Académica: una respuesta por docente visitado).
     */
    @ManyToOne(optional = true)
    @JoinColumn(name = "maestro_evaluado_id")
    @JsonIgnoreProperties({"asignaturas", "grupos", "horariosImpartidos", "usuario", "documentos"})
    private Maestro maestroEvaluado;

    /** Fecha y hora de la visita de observación (Evaluación Académica). */
    private LocalDateTime fechaVisita;

    /**
     * Informe u observaciones finales redactadas por administración para el docente (Evaluación Académica).
     * Texto largo; el docente puede consultarlo desde su portal.
     */
    @Column(name = "informe_para_docente", columnDefinition = "TEXT")
    private String informeParaDocente;

    /**
     * Cuándo el docente reconoció lectura del informe institucional ({@link #informeParaDocente}).
     * Si hay texto de informe y este campo es null, el informe cuenta como no leído para notificaciones.
     */
    @Column(name = "informe_leido_en")
    private LocalDateTime informeLeidoEn;

    /**
     * Observaciones/anotaciones por bloque (solo Evaluación Académica).
     * No se exponen al docente en la UI actual.
     */
    @ElementCollection
    @CollectionTable(
            name = "evaluacion_docente_respuesta_obs_bloque",
            joinColumns = @JoinColumn(name = "respuesta_id")
    )
    private List<EvaluacionDocenteObservacionBloque> observacionesBloque = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime fechaRespuesta;

    @OneToMany(mappedBy = "respuesta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"respuesta"})
    private List<EvaluacionDocenteItem> items = new ArrayList<>();
}
