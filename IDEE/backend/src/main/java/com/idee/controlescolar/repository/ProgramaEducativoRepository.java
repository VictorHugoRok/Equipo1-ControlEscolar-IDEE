package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.ProgramaEducativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para gestionar programas educativos.
 */
@Repository
public interface ProgramaEducativoRepository extends JpaRepository<ProgramaEducativo, Long> {

    /**
     * Busca un programa educativo por su clave
     */
    Optional<ProgramaEducativo> findByClave(String clave);

    /**
     * Verifica si existe un programa con la clave especificada
     */
    boolean existsByClave(String clave);
}
