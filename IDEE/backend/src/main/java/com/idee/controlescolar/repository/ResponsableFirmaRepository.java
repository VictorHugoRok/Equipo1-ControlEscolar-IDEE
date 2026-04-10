package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.ResponsableFirma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para gestionar los responsables autorizados
 * para firmar títulos profesionales electrónicos.
 */
@Repository
public interface ResponsableFirmaRepository extends JpaRepository<ResponsableFirma, Long> {

    /**
     * Busca todos los responsables activos ordenados por orden de firma
     *
     * @return Lista de responsables activos ordenados
     */
    List<ResponsableFirma> findByActivoTrueOrderByOrdenFirmaAsc();

    /**
     * Busca todos los responsables activos ordenados por orden de firma (alias)
     *
     * @return Lista de responsables activos ordenados
     */
    List<ResponsableFirma> findByActivoTrueOrderByOrdenFirma();

    /**
     * Busca un responsable por su CURP
     *
     * @param curp CURP del responsable
     * @return Optional con el responsable si existe
     */
    Optional<ResponsableFirma> findByCurp(String curp);

    /**
     * Verifica si existe un responsable con el CURP especificado
     *
     * @param curp CURP a verificar
     * @return true si existe un responsable con ese CURP
     */
    boolean existsByCurp(String curp);

    /**
     * Cuenta cuántos responsables activos hay
     *
     * @return Número de responsables activos
     */
    long countByActivoTrue();

    /**
     * Busca responsables por cargo
     *
     * @param cargo Cargo a buscar
     * @return Lista de responsables con ese cargo
     */
    List<ResponsableFirma> findByCargoContainingIgnoreCaseAndActivoTrue(String cargo);
}
