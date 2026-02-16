package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para calificaciones
 */
@Repository
public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {
    
    /**
     * Buscar calificaciones por ID de alumno
     */
    List<Calificacion> findByAlumnoId(Long alumnoId);
}
