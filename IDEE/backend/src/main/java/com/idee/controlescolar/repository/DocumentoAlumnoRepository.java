package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.DocumentoAlumno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentoAlumnoRepository extends JpaRepository<DocumentoAlumno, Long> {

    Optional<DocumentoAlumno> findByAlumno_IdAndTipoDocumento(Long alumnoId, DocumentoAlumno.TipoDocumento tipoDocumento);

    Optional<DocumentoAlumno> findByAlumno_IdAndTipoDocumentoAndDocSlot(
            Long alumnoId, DocumentoAlumno.TipoDocumento tipoDocumento, Integer docSlot);

    Optional<DocumentoAlumno> findByIdAndAlumno_Id(Long id, Long alumnoId);

    List<DocumentoAlumno> findByAlumno_IdOrderByTipoDocumentoAscDocSlotAsc(Long alumnoId);

    long countByAlumno_IdAndTipoDocumento(Long alumnoId, DocumentoAlumno.TipoDocumento tipoDocumento);
}
