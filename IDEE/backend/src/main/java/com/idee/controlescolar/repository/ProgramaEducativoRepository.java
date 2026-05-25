package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.ProgramaEducativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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
     * Busca un programa educativo por nombre (ignorando mayúsculas)
     */
    Optional<ProgramaEducativo> findByNombreIgnoreCase(String nombre);

    /**
     * Lista programas cuyo nombre contiene el texto (para carga masiva)
     */
    List<ProgramaEducativo> findByNombreContainingIgnoreCase(String nombre);

    /**
     * Busca un programa educativo por idPrograma (identificador de negocio)
     */
    Optional<ProgramaEducativo> findByIdPrograma(String idPrograma);

    /**
     * Verifica si existe un programa con la clave especificada
     */
    boolean existsByClave(String clave);

    /**
     * Verifica si existe un programa con el idPrograma especificado
     */
    boolean existsByIdPrograma(String idPrograma);

    /**
     * Verifica si existe otro programa (distinto al indicado) con el mismo idPrograma
     */
    boolean existsByIdProgramaAndIdNot(String idPrograma, Long id);

    /**
     * Verifica si existe otro programa (distinto al indicado) con la misma clave
     */
    boolean existsByClaveAndIdNot(String clave, Long id);

    List<ProgramaEducativo> findAllByOrderByNombreAsc();

    List<ProgramaEducativo> findByIdInOrderByNombreAsc(Collection<Long> ids);
}
