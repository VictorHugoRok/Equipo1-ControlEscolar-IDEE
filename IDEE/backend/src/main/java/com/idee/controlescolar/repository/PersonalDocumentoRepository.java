package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.PersonalDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalDocumentoRepository extends JpaRepository<PersonalDocumento, Long> {
    Optional<PersonalDocumento> findByPersonal_IdAndTipo(Long personalId, PersonalDocumento.Tipo tipo);
}

