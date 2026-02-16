package com.idee.controlescolar.service;

import com.idee.controlescolar.model.Calificacion;
import com.idee.controlescolar.repository.CalificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de Calificaciones
 * 
 * Gestiona operaciones CRUD de calificaciones de estudiantes.
 * 
 * Permisos:
 * - VER_CALIFICACIONES: ADMIN (solo lectura), SECRETARIA_ACADEMICA (lectura), MAESTRO (propias), ALUMNO (propias)
 * - REGISTRAR_CALIFICACIONES: MAESTRO, SECRETARIA_ACADEMICA
 * - EDITAR_CALIFICACIONES: SECRETARIA_ACADEMICA (solo si no está confirmada)
 * - CONFIRMAR_CALIFICACIONES: SECRETARIA_ACADEMICA
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CalificacionService {

    private final CalificacionRepository calificacionRepository;

    /**
     * Obtener todas las calificaciones
     * 
     * @return Lista de todas las calificaciones en el sistema
     */
    public List<Calificacion> obtenerTodas() {
        log.debug("Obteniendo todas las calificaciones");
        return calificacionRepository.findAll();
    }

    /**
     * Obtener calificación por ID
     * 
     * @param id ID de la calificación
     * @return Calificación encontrada
     * @throws IllegalArgumentException si no existe la calificación
     */
    public Calificacion obtenerPorId(Long id) {
        log.debug("Obteniendo calificación con ID: {}", id);
        return calificacionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Calificación no encontrada con ID: " + id));
    }

    /**
     * Crear una nueva calificación
     * 
     * @param calificacion Objeto calificación a crear
     * @return Calificación creada
     * @throws IllegalArgumentException si los datos son inválidos
     */
    public Calificacion crear(Calificacion calificacion) {
        log.info("Creando nueva calificación para alumno ID: {}, asignatura ID: {}",
            calificacion.getAlumno() != null ? calificacion.getAlumno().getId() : null,
            calificacion.getAsignatura() != null ? calificacion.getAsignatura().getId() : null);

        // Validaciones básicas
        if (calificacion.getAlumno() == null || calificacion.getAlumno().getId() == null) {
            throw new IllegalArgumentException("El alumno es requerido");
        }
        if (calificacion.getAsignatura() == null || calificacion.getAsignatura().getId() == null) {
            throw new IllegalArgumentException("La asignatura es requerida");
        }
        if (calificacion.getCalificacionFinal() == null) {
            throw new IllegalArgumentException("La calificación final es requerida");
        }
        if (calificacion.getCalificacionFinal() < 0 || calificacion.getCalificacionFinal() > 100) {
            throw new IllegalArgumentException("La calificación debe estar entre 0 y 100");
        }

        // Inicializar estado si no viene
        if (calificacion.getConfirmada() == null) {
            calificacion.setConfirmada(false);
        }
        if (calificacion.getEstadoAprobacion() == null) {
            calificacion.setEstadoAprobacion(Calificacion.EstadoAprobacion.PENDIENTE);
        }

        Calificacion guardada = calificacionRepository.save(calificacion);
        log.info("Calificación creada exitosamente con ID: {}", guardada.getId());
        return guardada;
    }

    /**
     * Actualizar una calificación existente
     * 
     * Solo se puede actualizar si NO está confirmada
     * 
     * @param id ID de la calificación
     * @param calificacionActualizada Nuevos datos
     * @return Calificación actualizada
     * @throws IllegalArgumentException si no existe o está confirmada
     */
    public Calificacion actualizar(Long id, Calificacion calificacionActualizada) {
        log.info("Actualizando calificación con ID: {}", id);

        Calificacion calificacionExistente = obtenerPorId(id);

        // Validar que no esté confirmada
        if (calificacionExistente.getConfirmada()) {
            throw new IllegalArgumentException("No se puede editar una calificación confirmada");
        }

        // Actualizar campos permitidos
        if (calificacionActualizada.getCalificacionFinal() != null) {
            if (calificacionActualizada.getCalificacionFinal() < 0 || 
                calificacionActualizada.getCalificacionFinal() > 100) {
                throw new IllegalArgumentException("La calificación debe estar entre 0 y 100");
            }
            calificacionExistente.setCalificacionFinal(calificacionActualizada.getCalificacionFinal());
        }

        if (calificacionActualizada.getAsistenciaPorcentaje() != null) {
            if (calificacionActualizada.getAsistenciaPorcentaje() < 0 || 
                calificacionActualizada.getAsistenciaPorcentaje() > 100) {
                throw new IllegalArgumentException("La asistencia debe estar entre 0 y 100");
            }
            calificacionExistente.setAsistenciaPorcentaje(calificacionActualizada.getAsistenciaPorcentaje());
        }

        if (calificacionActualizada.getObservaciones() != null) {
            calificacionExistente.setObservaciones(calificacionActualizada.getObservaciones());
        }

        if (calificacionActualizada.getTipoEvaluacion() != null) {
            calificacionExistente.setTipoEvaluacion(calificacionActualizada.getTipoEvaluacion());
        }

        if (calificacionActualizada.getPeriodo() != null) {
            calificacionExistente.setPeriodo(calificacionActualizada.getPeriodo());
        }

        Calificacion guardada = calificacionRepository.save(calificacionExistente);
        log.info("Calificación actualizada exitosamente con ID: {}", id);
        return guardada;
    }

    /**
     * Confirmar una calificación
     * 
     * Una vez confirmada, NO puede ser editada
     * 
     * @param id ID de la calificación
     * @throws IllegalArgumentException si no existe o ya está confirmada
     */
    public void confirmar(Long id) {
        log.info("Confirmando calificación con ID: {}", id);

        Calificacion calificacion = obtenerPorId(id);

        if (calificacion.getConfirmada()) {
            throw new IllegalArgumentException("La calificación ya está confirmada");
        }

        calificacion.setConfirmada(true);
        calificacion.setEstadoAprobacion(Calificacion.EstadoAprobacion.CONFIRMADA);
        calificacionRepository.save(calificacion);

        log.info("Calificación confirmada exitosamente con ID: {}", id);
    }

    /**
     * Eliminar una calificación
     * 
     * Solo se pueden eliminar calificaciones NO confirmadas
     * 
     * @param id ID de la calificación
     * @throws IllegalArgumentException si no existe o está confirmada
     */
    public void eliminar(Long id) {
        log.info("Eliminando calificación con ID: {}", id);

        Calificacion calificacion = obtenerPorId(id);

        if (calificacion.getConfirmada()) {
            throw new IllegalArgumentException("No se puede eliminar una calificación confirmada");
        }

        calificacionRepository.deleteById(id);
        log.info("Calificación eliminada exitosamente con ID: {}", id);
    }

    /**
     * Obtener estado de una calificación
     * 
     * @param id ID de la calificación
     * @return String con el estado (APROBADO/REPROBADO)
     */
    public String obtenerEstado(Long id) {
        log.debug("Obteniendo estado de calificación con ID: {}", id);

        Calificacion calificacion = obtenerPorId(id);
        
        if (calificacion.getEstatus() == null) {
            return "PENDIENTE";
        }

        return calificacion.getEstatus().name();
    }

    /**
     * Obtener calificaciones de un alumno
     * 
     * @param alumnoId ID del alumno
     * @return Lista de calificaciones del alumno
     */
    public List<Calificacion> obtenerPorAlumno(Long alumnoId) {
        log.debug("Obteniendo calificaciones del alumno ID: {}", alumnoId);
        return calificacionRepository.findByAlumnoId(alumnoId);
    }

    /**
     * Verificar si una calificación existe
     * 
     * @param id ID de la calificación
     * @return true si existe, false si no
     */
    public boolean existe(Long id) {
        return calificacionRepository.existsById(id);
    }

    /**
     * Obtener cantidad total de calificaciones
     * 
     * @return Cantidad de registros
     */
    public long contar() {
        return calificacionRepository.count();
    }
}
