package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.EstatusTitulo;
import com.idee.controlescolar.model.TituloElectronico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para gestionar los títulos profesionales electrónicos.
 */
@Repository
public interface TituloElectronicoRepository extends JpaRepository<TituloElectronico, Long> {

    /**
     * Busca un título por su folio de control
     *
     * @param folioControl Folio de control del título
     * @return Optional con el título si existe
     */
    Optional<TituloElectronico> findByFolioControl(String folioControl);

    /**
     * Busca todos los títulos de un alumno
     *
     * @param alumnoId ID del alumno
     * @return Lista de títulos del alumno
     */
    List<TituloElectronico> findByAlumnoId(Long alumnoId);

    /**
     * Busca títulos por estatus
     *
     * @param estatus Estatus del título
     * @return Lista de títulos con ese estatus
     */
    List<TituloElectronico> findByEstatus(EstatusTitulo estatus);

    /**
     * Verifica si existe un título con el folio de control especificado
     *
     * @param folioControl Folio de control a verificar
     * @return true si existe un título con ese folio
     */
    boolean existsByFolioControl(String folioControl);

    /**
     * Busca títulos por programa educativo
     *
     * @param programaId ID del programa educativo
     * @return Lista de títulos del programa
     */
    List<TituloElectronico> findByProgramaId(Long programaId);

    /**
     * Busca títulos por rango de fechas de expedición
     *
     * @param fechaInicio Fecha inicio del rango
     * @param fechaFin Fecha fin del rango
     * @return Lista de títulos en ese rango
     */
    List<TituloElectronico> findByFechaExpedicionBetween(LocalDate fechaInicio, LocalDate fechaFin);

    /**
     * Busca títulos por alumno y estatus
     *
     * @param alumnoId ID del alumno
     * @param estatus Estatus del título
     * @return Lista de títulos
     */
    List<TituloElectronico> findByAlumnoIdAndEstatus(Long alumnoId, EstatusTitulo estatus);

    /**
     * Cuenta títulos por estatus
     *
     * @param estatus Estatus a contar
     * @return Cantidad de títulos con ese estatus
     */
    long countByEstatus(EstatusTitulo estatus);

    /**
     * Busca títulos pendientes de firma (GENERADO)
     *
     * @return Lista de títulos pendientes
     */
    @Query("SELECT t FROM TituloElectronico t WHERE t.estatus = 'GENERADO' ORDER BY t.fechaCreacion DESC")
    List<TituloElectronico> findTitulosPendientesFirma();

    /**
     * Busca el último título generado para un alumno
     *
     * @param alumnoId ID del alumno
     * @return Optional con el último título si existe
     */
    @Query("SELECT t FROM TituloElectronico t WHERE t.alumno.id = :alumnoId ORDER BY t.fechaCreacion DESC LIMIT 1")
    Optional<TituloElectronico> findUltimoTituloPorAlumno(@Param("alumnoId") Long alumnoId);

    /**
     * Verifica si un alumno ya tiene un título
     *
     * @param alumnoId ID del alumno
     * @return true si el alumno ya tiene al menos un título
     */
    boolean existsByAlumnoId(Long alumnoId);
}
