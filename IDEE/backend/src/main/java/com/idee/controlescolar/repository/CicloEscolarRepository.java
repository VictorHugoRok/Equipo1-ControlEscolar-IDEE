package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.CicloEscolar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CicloEscolarRepository extends JpaRepository<CicloEscolar, Long> {

    List<CicloEscolar> findAllByOrderByFechaInicioDesc();
}
