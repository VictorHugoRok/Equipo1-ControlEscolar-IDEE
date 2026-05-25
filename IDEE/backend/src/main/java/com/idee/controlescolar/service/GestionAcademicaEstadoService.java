package com.idee.controlescolar.service;

import com.idee.controlescolar.model.EstadoGestionPeriodoAcademico;
import com.idee.controlescolar.model.PeriodoAcademico;
import org.springframework.stereotype.Service;

@Service
public class GestionAcademicaEstadoService {

    public EstadoGestionPeriodoAcademico estado(PeriodoAcademico periodo) {
        if (periodo == null || periodo.getEstadoGestion() == null) {
            return EstadoGestionPeriodoAcademico.INACTIVO;
        }
        return periodo.getEstadoGestion();
    }

    public boolean esPlaneacion(PeriodoAcademico periodo) {
        return estado(periodo) == EstadoGestionPeriodoAcademico.INACTIVO;
    }

    public boolean esOperacionActiva(PeriodoAcademico periodo) {
        return estado(periodo) == EstadoGestionPeriodoAcademico.ACTIVO;
    }

    public boolean esHistorico(PeriodoAcademico periodo) {
        return estado(periodo) == EstadoGestionPeriodoAcademico.CERRADO;
    }

    public String etiquetaEtapa(PeriodoAcademico periodo) {
        return switch (estado(periodo)) {
            case INACTIVO -> "Planeacion";
            case ACTIVO -> "Activo";
            case CERRADO -> "Historico";
        };
    }

    public String descripcionPeriodo(PeriodoAcademico periodo) {
        if (periodo == null) {
            return "el contexto academico actual";
        }
        String codigo = (periodo.getCodigo() != null && !periodo.getCodigo().isBlank())
                ? "el periodo academico \"" + periodo.getCodigo().trim() + "\""
                : "el periodo academico seleccionado";
        return codigo + " (" + etiquetaEtapa(periodo).toLowerCase() + ")";
    }

    public String validarEdicionHorario(PeriodoAcademico periodo) {
        if (periodo == null) {
            return null;
        }
        if (esHistorico(periodo)) {
            return "No se puede editar el horario porque " + descripcionPeriodo(periodo)
                    + " ya esta en historico.";
        }
        return null;
    }

    public String validarEdicionGrupo(PeriodoAcademico periodo) {
        if (periodo == null) {
            return null;
        }
        if (esHistorico(periodo)) {
            return "No se puede modificar el grupo porque " + descripcionPeriodo(periodo)
                    + " ya esta en historico.";
        }
        return null;
    }

    public String validarInscripcion(PeriodoAcademico periodo) {
        if (periodo == null) {
            return "Debes seleccionar un periodo academico en estado Activo para continuar con la inscripcion.";
        }
        if (!esOperacionActiva(periodo)) {
            return "Las inscripciones solo se permiten cuando " + descripcionPeriodo(periodo)
                    + " esta en activo.";
        }
        return null;
    }

    public String validarCapturaCalificacion(PeriodoAcademico periodo) {
        if (periodo == null) {
            return null;
        }
        if (esPlaneacion(periodo)) {
            return "Las calificaciones solo se capturan cuando " + descripcionPeriodo(periodo)
                    + " ya esta en activo.";
        }
        if (esHistorico(periodo)) {
            return "No se pueden capturar ni confirmar calificaciones porque " + descripcionPeriodo(periodo)
                    + " ya esta en historico.";
        }
        return null;
    }

    public String validarCorreccionCalificacion(PeriodoAcademico periodo) {
        if (periodo == null) {
            return null;
        }
        if (esPlaneacion(periodo)) {
            return "No hay calificaciones oficiales para corregir mientras " + descripcionPeriodo(periodo)
                    + " sigue en planeacion.";
        }
        return null;
    }

    public String validarNuevaEvaluacion(PeriodoAcademico periodo) {
        if (periodo == null) {
            return null;
        }
        if (!esOperacionActiva(periodo)) {
            return "Las respuestas de evaluacion docente solo se registran cuando "
                    + descripcionPeriodo(periodo) + " esta en activo.";
        }
        return null;
    }
}
