package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Cohorte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CohorteRepository extends JpaRepository<Cohorte, Long> {

    @Query("SELECT c FROM Cohorte c ORDER BY LOWER(COALESCE(c.nombre,'')), c.id")
    List<Cohorte> findAllOrderByNombreAscIgnoreCase();
    Optional<Cohorte> findByIdCohorte(String idCohorte);
    boolean existsByIdCohorte(String idCohorte);
    boolean existsByIdCohorteAndIdNot(String idCohorte, Long id);
}
