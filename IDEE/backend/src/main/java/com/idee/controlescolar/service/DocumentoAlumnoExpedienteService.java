package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.AlumnoDocumentoMeta;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.repository.DocumentoAlumnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentoAlumnoExpedienteService {

    public static final int TITULO_CEDULA_MAX_SLOTS = 4;

    private final DocumentoAlumnoRepository documentoAlumnoRepository;

    /**
     * Documento único por tipo (doc_slot = 0). No usar para {@link DocumentoAlumno.TipoDocumento#TITULO_CEDULA}.
     */
    public Optional<DocumentoAlumno> buscar(Long alumnoId, DocumentoAlumno.TipoDocumento tipo) {
        if (alumnoId == null || tipo == null) {
            return Optional.empty();
        }
        if (tipo == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            return Optional.empty();
        }
        return documentoAlumnoRepository.findByAlumno_IdAndTipoDocumentoAndDocSlot(alumnoId, tipo, 0);
    }

    public Optional<DocumentoAlumno> buscarTituloCedulaSlot(Long alumnoId, int docSlot) {
        if (alumnoId == null || docSlot < 1 || docSlot > TITULO_CEDULA_MAX_SLOTS) {
            return Optional.empty();
        }
        return documentoAlumnoRepository.findByAlumno_IdAndTipoDocumentoAndDocSlot(
                alumnoId, DocumentoAlumno.TipoDocumento.TITULO_CEDULA, docSlot);
    }

    public Optional<DocumentoAlumno> buscarPorIdEnAlumno(Long alumnoId, Long documentoId) {
        if (alumnoId == null || documentoId == null) {
            return Optional.empty();
        }
        return documentoAlumnoRepository.findByIdAndAlumno_Id(documentoId, alumnoId);
    }

    public DocumentoAlumno obtenerOCrearYVincular(Alumno alumno, DocumentoAlumno.TipoDocumento tipo) {
        if (tipo == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            throw new IllegalArgumentException("Use obtenerOCrearTituloCedulaEnSlot para TITULO_CEDULA");
        }
        return obtenerOCrearYVincularEnSlot(alumno, tipo, 0);
    }

    @Transactional
    public DocumentoAlumno obtenerOCrearTituloCedulaEnSlot(Alumno alumno, int docSlot) {
        if (alumno == null) {
            throw new IllegalArgumentException("Alumno requerido");
        }
        if (docSlot < 1 || docSlot > TITULO_CEDULA_MAX_SLOTS) {
            throw new IllegalArgumentException("doc_slot debe ser 1..4");
        }
        if (alumno.getId() != null) {
            Optional<DocumentoAlumno> opt = documentoAlumnoRepository.findByAlumno_IdAndTipoDocumentoAndDocSlot(
                    alumno.getId(), DocumentoAlumno.TipoDocumento.TITULO_CEDULA, docSlot);
            if (opt.isPresent()) {
                DocumentoAlumno d = opt.get();
                vincularSiFaltaEnMemoria(alumno, d);
                return d;
            }
            long n = documentoAlumnoRepository.countByAlumno_IdAndTipoDocumento(
                    alumno.getId(), DocumentoAlumno.TipoDocumento.TITULO_CEDULA);
            if (n >= TITULO_CEDULA_MAX_SLOTS) {
                throw new IllegalStateException("Ya hay " + TITULO_CEDULA_MAX_SLOTS + " registros de título/cédula");
            }
        }
        DocumentoAlumno n = new DocumentoAlumno();
        n.setAlumno(alumno);
        n.setTipoDocumento(DocumentoAlumno.TipoDocumento.TITULO_CEDULA);
        n.setDocSlot(docSlot);
        n.setEntregado(false);
        if (alumno.getDocumentos() == null) {
            alumno.setDocumentos(new ArrayList<>());
        }
        alumno.getDocumentos().add(n);
        return n;
    }

    private DocumentoAlumno obtenerOCrearYVincularEnSlot(Alumno alumno, DocumentoAlumno.TipoDocumento tipo, int docSlot) {
        if (alumno == null || tipo == null) {
            throw new IllegalArgumentException("Alumno y tipo requeridos");
        }
        if (alumno.getId() != null) {
            Optional<DocumentoAlumno> opt = documentoAlumnoRepository.findByAlumno_IdAndTipoDocumentoAndDocSlot(
                    alumno.getId(), tipo, docSlot);
            if (opt.isPresent()) {
                DocumentoAlumno d = opt.get();
                vincularSiFaltaEnMemoria(alumno, d);
                return d;
            }
        }
        DocumentoAlumno n = new DocumentoAlumno();
        n.setAlumno(alumno);
        n.setTipoDocumento(tipo);
        n.setDocSlot(docSlot);
        n.setEntregado(false);
        if (alumno.getDocumentos() == null) {
            alumno.setDocumentos(new ArrayList<>());
        }
        alumno.getDocumentos().add(n);
        return n;
    }

    private void vincularSiFaltaEnMemoria(Alumno alumno, DocumentoAlumno d) {
        if (alumno.getDocumentos() == null) {
            alumno.setDocumentos(new ArrayList<>());
        }
        for (DocumentoAlumno x : alumno.getDocumentos()) {
            if (x != null && d.getId() != null && d.getId().equals(x.getId())) {
                return;
            }
        }
        alumno.getDocumentos().add(d);
    }

    @Transactional
    public void limpiarArchivo(Long alumnoId, DocumentoAlumno.TipoDocumento tipo) {
        if (tipo == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            throw new IllegalArgumentException("Use limpiarArchivoPorDocId para TITULO_CEDULA");
        }
        buscar(alumnoId, tipo).ifPresent(doc -> {
            doc.setArchivoUrl(null);
            doc.setEntregado(false);
            doc.setFechaRecepcion(null);
            documentoAlumnoRepository.save(doc);
        });
    }

    @Transactional
    public void limpiarArchivoPorDocId(Long alumnoId, Long documentoId) {
        buscarPorIdEnAlumno(alumnoId, documentoId).ifPresent(doc -> {
            doc.setArchivoUrl(null);
            doc.setEntregado(false);
            doc.setFechaRecepcion(null);
            documentoAlumnoRepository.save(doc);
        });
    }

    @Transactional
    public void aplicarMetadatosTituloCedula(Long alumnoId, Long documentoId, String etiqueta, String numeroCedula) {
        buscarPorIdEnAlumno(alumnoId, documentoId).ifPresent(doc -> {
            if (doc.getTipoDocumento() != DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                return;
            }
            if (etiqueta != null) {
                doc.setEtiquetaDocumento(etiqueta.isBlank() ? null : etiqueta.trim());
            }
            if (numeroCedula != null) {
                doc.setNumeroCedula(numeroCedula.isBlank() ? null : numeroCedula.trim());
            }
            documentoAlumnoRepository.save(doc);
        });
    }

    public List<AlumnoDocumentoMeta> listarMetadatos(Long alumnoId) {
        if (alumnoId == null) {
            return List.of();
        }
        return documentoAlumnoRepository.findByAlumno_IdOrderByTipoDocumentoAscDocSlotAsc(alumnoId).stream()
                .filter(d -> d != null && d.getTipoDocumento() != null)
                .map(d -> {
                    boolean ent = Boolean.TRUE.equals(d.getEntregado())
                            || (d.getArchivoUrl() != null && !d.getArchivoUrl().isBlank());
                    String desc = d.getTipoDocumento().getDescripcion();
                    if (d.getTipoDocumento() == DocumentoAlumno.TipoDocumento.TITULO_CEDULA
                            && d.getEtiquetaDocumento() != null && !d.getEtiquetaDocumento().isBlank()) {
                        desc = d.getEtiquetaDocumento().trim();
                    }
                    return new AlumnoDocumentoMeta(
                            d.getId(),
                            d.getTipoDocumento().name(),
                            desc,
                            d.getDocSlot(),
                            d.getEtiquetaDocumento(),
                            d.getNumeroCedula(),
                            ent,
                            d.getFechaRecepcion(),
                            extraerFilename(d.getArchivoUrl()),
                            d.getOrigenUltimaCarga() != null ? d.getOrigenUltimaCarga().name() : null,
                            d.getCargadoPorUsuarioId()
                    );
                })
                .sorted(Comparator
                        .comparing(AlumnoDocumentoMeta::getTipo)
                        .thenComparing(m -> m.getDocSlot() != null ? m.getDocSlot() : 0))
                .toList();
    }

    private static String extraerFilename(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }
        String s = pathOrUrl.trim();
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        String fn = slash >= 0 ? s.substring(slash + 1) : s;
        return fn.isBlank() ? null : fn;
    }
}
