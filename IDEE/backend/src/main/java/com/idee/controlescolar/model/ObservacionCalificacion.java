package com.idee.controlescolar.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catálogo de observaciones predefinidas para calificaciones.
 * IDs y descripciones según estándar SEP para certificados electrónicos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObservacionCalificacion {

    private Integer id;
    private String observacion;
    private String descripcionCorta;

    /** ID por defecto: ORDINARIO */
    public static final int ID_DEFAULT = 100;

    private static final List<ObservacionCalificacion> CATALOGO = Arrays.asList(
            new ObservacionCalificacion(70, "EQUIVALENCIA DE ESTUDIOS", "E."),
            new ObservacionCalificacion(71, "EXAMEN EXTRAORDINARIO", "E.E."),
            new ObservacionCalificacion(72, "EXAMEN A TITULO DE SUFICIENCIA", "E.T.S."),
            new ObservacionCalificacion(73, "CURSO DE VERANO", "C.V."),
            new ObservacionCalificacion(74, "RECURSAMIENTO", "Rec."),
            new ObservacionCalificacion(75, "REINGRESO", "Rein."),
            new ObservacionCalificacion(76, "ACUERDO REGULARIZACION", "A.C."),
            new ObservacionCalificacion(77, "CON CAMBIO EN EL ACUERDO DE RVOE", "C.A"),
            new ObservacionCalificacion(78, "REVALIDACIÓN DE ESTUDIOS", "R."),
            new ObservacionCalificacion(100, "ORDINARIO", "ORDINARIO"),
            new ObservacionCalificacion(101, "CORRESPONDENCIA DE ASIGNATURA POR PLAN", "C.A.P."),
            new ObservacionCalificacion(102, "EXENTO", "EX."),
            new ObservacionCalificacion(104, "CURSO DE REGULARIZACIÓN", "C.R."),
            new ObservacionCalificacion(105, "INTERCAMBIO ACADEMICO", "I.A."),
            new ObservacionCalificacion(106, "EXAMEN DE ULTIMA MATERIA", "E.U.M."),
            new ObservacionCalificacion(107, "CURSO DE INVIERNO", "C.I.")
    );

    private static final Map<Integer, ObservacionCalificacion> POR_ID = CATALOGO.stream()
            .collect(Collectors.toMap(ObservacionCalificacion::getId, o -> o));

    public static List<ObservacionCalificacion> getCatalogo() {
        return CATALOGO;
    }

    public static ObservacionCalificacion getPorId(Integer id) {
        return id != null ? POR_ID.get(id) : null;
    }

    /** Obtiene la descripción corta para el XML de certificados. Si no existe, retorna null. */
    public static String getDescripcionPorId(Integer id) {
        ObservacionCalificacion o = getPorId(id);
        return o != null ? o.getDescripcionCorta() : null;
    }
}
