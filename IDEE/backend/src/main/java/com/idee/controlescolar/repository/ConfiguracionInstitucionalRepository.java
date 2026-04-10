package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.ConfiguracionInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para gestionar la configuración institucional
 * para la emisión de títulos electrónicos.
 */
@Repository
public interface ConfiguracionInstitucionalRepository extends JpaRepository<ConfiguracionInstitucional, Long> {

    /**
     * Busca la configuración institucional activa.
     * Solo puede haber una configuración activa a la vez.
     *
     * @return Optional con la configuración activa si existe
     */
    Optional<ConfiguracionInstitucional> findByActivoTrue();

    /**
     * Verifica si existe una configuración activa
     *
     * @return true si existe una configuración activa
     */
    boolean existsByActivoTrue();
}
