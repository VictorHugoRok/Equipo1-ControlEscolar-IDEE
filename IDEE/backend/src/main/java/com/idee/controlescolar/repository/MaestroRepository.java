package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Maestro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para gestionar maestros
 */
@Repository
public interface MaestroRepository extends JpaRepository<Maestro, Long> {

    boolean existsByCurp(String curp);

    boolean existsByCurpAndIdNot(String curp, Long id);

    Optional<Maestro> findByUsuarioId(Long usuarioId);
}
