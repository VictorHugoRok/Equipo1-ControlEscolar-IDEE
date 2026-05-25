package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.MaestroDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaestroDocumentoRepository extends JpaRepository<MaestroDocumento, Long> {

    Optional<MaestroDocumento> findByMaestro_IdAndTipoDocumento(Long maestroId, String tipoDocumento);

    List<MaestroDocumento> findByMaestro_IdOrderByTipoDocumentoAsc(Long maestroId);
}
