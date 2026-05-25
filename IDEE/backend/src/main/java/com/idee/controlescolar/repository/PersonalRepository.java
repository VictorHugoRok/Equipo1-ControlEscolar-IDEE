package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Personal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PersonalRepository extends JpaRepository<Personal, Long> {

    @Query("SELECT DISTINCT p FROM Personal p LEFT JOIN FETCH p.cedulasProfesionales WHERE p.id = :id")
    Optional<Personal> findByIdWithCedulasProfesionales(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Personal p LEFT JOIN FETCH p.documentos WHERE p.id = :id")
    Optional<Personal> findByIdWithDocumentos(@Param("id") Long id);
    
    /**
     * Buscar personal por CURP
     */
    Optional<Personal> findByCurp(String curp);
    
    /**
     * Verificar si existe personal con CURP
     */
    boolean existsByCurp(String curp);
    
    /**
     * Buscar personal por correo institucional
     */
    Optional<Personal> findByCorreoInstitucional(String correoInstitucional);

    Optional<Personal> findByUsuario_Id(Long usuarioId);
    
    /**
     * Buscar personal por puesto
     */
    List<Personal> findByPuesto(String puesto);
    
    /**
     * Buscar personal activo
     */
    List<Personal> findByActivoTrue();
    
    /**
     * Buscar personal inactivo
     */
    List<Personal> findByActivoFalse();
    
    /**
     * Verificar si existe CURP excluyendo un ID
     */
    boolean existsByCurpAndIdNot(String curp, Long id);
    
    /**
     * Buscar por nombre o apellido (búsqueda parcial)
     */
    List<Personal> findByNombreContainingIgnoreCaseOrApellidoPaternoContainingIgnoreCaseOrApellidoMaternoContainingIgnoreCase(
            String nombre, String apellidoPaterno, String apellidoMaterno);
}
