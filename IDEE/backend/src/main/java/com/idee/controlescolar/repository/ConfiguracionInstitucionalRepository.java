package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.ConfiguracionInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para gestionar la configuración institucional
 * para la emisión de títulos electrónicos.
 * Solo puede existir UNA configuración activa a la vez.
 */
@Repository
public interface ConfiguracionInstitucionalRepository extends JpaRepository<ConfiguracionInstitucional, Long> {

    /**
     * Busca la configuración institucional activa.
     *
     * @return Optional con la configuración activa si existe
     */
    Optional<ConfiguracionInstitucional> findFirstByActivoTrueOrderByIdDesc();

    /**
     * Verifica si existe una configuración activa
     */
    boolean existsByActivoTrue();

    /**
     * Cuenta cuántas configuraciones tienen activo=true.
     */
    @Query("SELECT COUNT(c) FROM ConfiguracionInstitucional c WHERE c.activo = true")
    long countByActivoTrue();

    /**
     * Desactiva todas las configuraciones excepto la indicada.
     * Garantiza que solo una esté activa.
     */
    @Modifying
    @Query("UPDATE ConfiguracionInstitucional c SET c.activo = false WHERE c.id != :id")
    void desactivarOtras(@Param("id") Long id);

    /**
     * Desactiva todas las configuraciones.
     */
    @Modifying
    @Query("UPDATE ConfiguracionInstitucional c SET c.activo = false")
    void desactivarTodas();
}
