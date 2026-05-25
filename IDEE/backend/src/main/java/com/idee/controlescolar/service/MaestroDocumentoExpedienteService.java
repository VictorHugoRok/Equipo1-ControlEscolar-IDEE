package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.AlumnoDocumentoMeta;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.MaestroDocumento;
import com.idee.controlescolar.repository.MaestroDocumentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MaestroDocumentoExpedienteService {

    private final MaestroDocumentoRepository maestroDocumentoRepository;

    public Optional<MaestroDocumento> buscar(Long maestroId, String tipoDocumento) {
        if (maestroId == null || tipoDocumento == null || tipoDocumento.isBlank()) {
            return Optional.empty();
        }
        return maestroDocumentoRepository.findByMaestro_IdAndTipoDocumento(maestroId, tipoDocumento.trim());
    }

    public MaestroDocumento obtenerOCrearYVincular(Maestro maestro, String tipoDocumento) {
        if (maestro == null || tipoDocumento == null || tipoDocumento.isBlank()) {
            throw new IllegalArgumentException("Maestro y tipo requeridos");
        }
        String tipo = tipoDocumento.trim();
        if (maestro.getId() != null) {
            Optional<MaestroDocumento> opt = maestroDocumentoRepository.findByMaestro_IdAndTipoDocumento(maestro.getId(), tipo);
            if (opt.isPresent()) {
                MaestroDocumento d = opt.get();
                vincularSiFaltaEnMemoria(maestro, d);
                return d;
            }
        }
        MaestroDocumento n = new MaestroDocumento();
        n.setMaestro(maestro);
        n.setTipoDocumento(tipo);
        if (maestro.getDocumentos() == null) {
            maestro.setDocumentos(new ArrayList<>());
        }
        maestro.getDocumentos().add(n);
        return n;
    }

    private void vincularSiFaltaEnMemoria(Maestro maestro, MaestroDocumento d) {
        if (maestro.getDocumentos() == null) {
            maestro.setDocumentos(new ArrayList<>());
        }
        for (MaestroDocumento x : maestro.getDocumentos()) {
            if (x != null && d.getId() != null && d.getId().equals(x.getId())) {
                return;
            }
        }
        maestro.getDocumentos().add(d);
    }

    public List<AlumnoDocumentoMeta> listarMetadatos(Long maestroId) {
        if (maestroId == null) {
            return List.of();
        }
        return maestroDocumentoRepository.findByMaestro_IdOrderByTipoDocumentoAsc(maestroId).stream()
                .filter(d -> d != null && d.getTipoDocumento() != null && !d.getTipoDocumento().isBlank())
                .map(d -> {
                    boolean ent = d.getData() != null && d.getData().length > 0;
                    return new AlumnoDocumentoMeta(
                            d.getId(),
                            d.getTipoDocumento(),
                            descripcionHumana(d.getTipoDocumento()),
                            0,
                            null,
                            null,
                            ent,
                            d.getFechaCreacion() != null ? d.getFechaCreacion().toLocalDate() : null,
                            d.getFilename(),
                            null,
                            null
                    );
                })
                .sorted(Comparator.comparing(AlumnoDocumentoMeta::getTipo))
                .toList();
    }

    public static String descripcionHumana(String tipo) {
        if (tipo == null) {
            return "—";
        }
        try {
            return DocumentoAlumno.TipoDocumento.valueOf(tipo).getDescripcion();
        } catch (IllegalArgumentException ignored) {
            if (tipo.startsWith("ANT_")) {
                try {
                    int n = Integer.parseInt(tipo.substring(4), 10) + 1;
                    return "Antecedente " + n;
                } catch (NumberFormatException e) {
                    return tipo;
                }
            }
            if (tipo.startsWith("LEGACY_")) {
                return "Documento (archivo previo)";
            }
            return tipo;
        }
    }

    public static boolean tieneContenido(MaestroDocumento d) {
        return d != null && d.getData() != null && d.getData().length > 0;
    }

    /** Retira el archivo (p. ej. para que el docente pueda subir otro desde el portal). */
    @Transactional
    public void limpiarContenido(Long maestroId, String tipoDocumento) {
        if (maestroId == null || tipoDocumento == null || tipoDocumento.isBlank()) {
            return;
        }
        buscar(maestroId, tipoDocumento.trim()).ifPresent(doc -> {
            doc.setData(new byte[0]);
            doc.setSizeBytes(0L);
            maestroDocumentoRepository.save(doc);
        });
    }
}
