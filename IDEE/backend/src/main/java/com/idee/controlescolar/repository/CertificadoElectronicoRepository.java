package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.CertificadoElectronico;
import com.idee.controlescolar.model.EstatusCertificado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para certificados electrónicos (MEC).
 * Mismo patrón que TituloElectronicoRepository.
 */
@Repository
public interface CertificadoElectronicoRepository extends JpaRepository<CertificadoElectronico, Long> {

    Optional<CertificadoElectronico> findByFolioControl(String folioControl);

    List<CertificadoElectronico> findByAlumnoId(Long alumnoId);

    List<CertificadoElectronico> findByEstatus(EstatusCertificado estatus);

    boolean existsByFolioControl(String folioControl);

    Optional<CertificadoElectronico> findTop1ByFolioControlStartingWithOrderByFolioControlDesc(String prefix);

    List<CertificadoElectronico> findByProgramaId(Long programaId);

    List<CertificadoElectronico> findByFechaExpedicionBetween(LocalDate fechaInicio, LocalDate fechaFin);

    List<CertificadoElectronico> findByAlumnoIdAndEstatus(Long alumnoId, EstatusCertificado estatus);

    long countByEstatus(EstatusCertificado estatus);

    @Query("SELECT c FROM CertificadoElectronico c WHERE c.estatus = 'GENERADO' ORDER BY c.fechaCreacion DESC")
    List<CertificadoElectronico> findCertificadosPendientesFirma();

    Optional<CertificadoElectronico> findTop1ByAlumnoIdOrderByFechaCreacionDesc(Long alumnoId);
}
