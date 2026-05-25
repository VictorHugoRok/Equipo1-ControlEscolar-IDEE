package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByEmailAndActivoTrue(String email);

    Optional<Usuario> findByEmailIgnoreCaseAndActivoTrue(String email);

    @Query(value = "SELECT programa_id FROM usuario_programa_asignado WHERE usuario_id = :usuarioId", nativeQuery = true)
    List<Long> findProgramaIdsAsignados(@Param("usuarioId") Long usuarioId);
}
