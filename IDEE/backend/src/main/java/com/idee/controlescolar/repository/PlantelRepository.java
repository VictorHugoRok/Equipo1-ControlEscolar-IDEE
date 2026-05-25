package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.Plantel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlantelRepository extends JpaRepository<Plantel, Long> {

    Optional<Plantel> findByClaveDgp(String claveDgp);

    @Query("SELECT p FROM Plantel p WHERE LOWER(TRIM(p.claveDgp)) = LOWER(TRIM(:clave))")
    Optional<Plantel> findByClaveDgpIgnoreCaseTrim(@Param("clave") String claveDgp);

    boolean existsByClaveDgp(String claveDgp);
}
