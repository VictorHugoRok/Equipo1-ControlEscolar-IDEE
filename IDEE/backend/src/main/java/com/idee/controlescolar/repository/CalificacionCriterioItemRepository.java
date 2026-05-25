package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.CalificacionCriterioItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalificacionCriterioItemRepository extends JpaRepository<CalificacionCriterioItem, Long> {
    List<CalificacionCriterioItem> findByCalificacion_Id(Long calificacionId);

    @Modifying
    @Transactional
    void deleteByCalificacion_Id(Long calificacionId);
}

