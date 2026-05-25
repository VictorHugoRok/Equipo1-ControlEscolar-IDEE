package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "asignaturas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Asignatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_asignatura")
    private String idAsignatura;

    @Column(nullable = false)
    private String clave;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    private TipoAsignatura tipo;

    /**
     * Periodo del plan de estudios al que pertenece esta asignatura.
     * Reemplaza el antiguo campo periodo (Integer).
     */
    @ManyToOne
    @JoinColumn(name = "periodo_id")
    @JsonIgnoreProperties({"asignaturas", "programa", "hibernateLazyInitializer", "handler"})
    private Periodo periodo;

    private Integer creditos;

    /** Horas semanales de clase con el profesor (aula). */
    private Integer horasAula;

    /** Horas semanales de laboratorio, taller o práctica. */
    private Integer horasPractica;

    /** Horas semanales de estudio independiente. */
    private Integer horasIndependientes;

    @Enumerated(EnumType.STRING)
    private EstatusAsignatura estatus = EstatusAsignatura.ACTIVA;

    @ManyToOne
    @JoinColumn(name = "programa_id")
    @JsonIgnoreProperties({"asignaturas", "alumnos", "hibernateLazyInitializer", "handler"})
    private ProgramaEducativo programa;

    @ManyToMany
    @JoinTable(
        name = "asignatura_maestro",
        joinColumns = @JoinColumn(name = "asignatura_id"),
        inverseJoinColumns = @JoinColumn(name = "maestro_id")
    )
    @JsonIgnore
    private List<Maestro> maestros = new ArrayList<>();

    @OneToMany(mappedBy = "asignatura")
    @JsonIgnore
    private List<Grupo> grupos = new ArrayList<>();

    public enum TipoAsignatura {
        OBLIGATORIA,
        OPTATIVA,
        LIBRE,
        EXTRACURRICULAR,
        SERVICIO_SOCIAL,
        RESIDENCIA_PROFESIONAL
    }

    public enum EstatusAsignatura {
        ACTIVA,
        INACTIVA
    }

    private static final Set<TipoAsignatura> TIPOS_QUE_CUENTAN_EN_PLAN = Set.of(
            TipoAsignatura.OBLIGATORIA,
            TipoAsignatura.OPTATIVA,
            TipoAsignatura.LIBRE
    );

    /**
     * Si cuenta en kardex, certificados, créditos, periodos completos y calificaciones obligatorias.
     */
    @JsonIgnore
    public boolean cuentaEnPlanAcademico() {
        // Compatibilidad: si no se capturó tipo en datos antiguos, asumimos que SÍ cuenta en el plan.
        if (tipo == null) return true;
        return TIPOS_QUE_CUENTAN_EN_PLAN.contains(tipo);
    }

    /** Número del periodo (1, 2, 3...) para compatibilidad con APIs que esperan un número. */
    public Integer getPeriodoNumero() {
        return periodo != null ? periodo.getNumero() : null;
    }

    /**
     * Orden estable por identificador de negocio {@code idAsignatura} (orden numérico si el valor es numérico);
     * valores vacíos o nulos al final; desempate por {@code id} interno.
     */
    public static void ordenarListaPorIdAsignatura(List<Asignatura> lista) {
        if (lista == null || lista.size() <= 1) {
            return;
        }
        lista.sort(Comparator
                .comparing(Asignatura::idAsignaturaTrimmedONulo, Comparator.nullsLast(Asignatura::compareIdAsignaturaNatural))
                .thenComparing(Asignatura::getId, Comparator.nullsLast(Long::compare)));
    }

    private static String idAsignaturaTrimmedONulo(Asignatura a) {
        if (a == null || a.getIdAsignatura() == null) {
            return null;
        }
        String t = a.getIdAsignatura().trim();
        return t.isEmpty() ? null : t;
    }

    private static int compareIdAsignaturaNatural(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }
}
