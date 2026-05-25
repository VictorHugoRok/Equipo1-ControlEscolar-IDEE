package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.PersonalCedulaProfesional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonalCedulaProfesionalRepository extends JpaRepository<PersonalCedulaProfesional, Long> {

    List<PersonalCedulaProfesional> findByPersonal_IdOrderByIdAsc(Long personalId);

    void deleteByPersonal_Id(Long personalId);
}
