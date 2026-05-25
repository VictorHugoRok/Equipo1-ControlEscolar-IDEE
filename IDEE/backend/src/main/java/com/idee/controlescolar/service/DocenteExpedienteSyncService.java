package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.AlumnoDocumentoMeta;
import com.idee.controlescolar.dto.StaffSaveResponse;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.MaestroDocumento;
import com.idee.controlescolar.model.Personal;
import com.idee.controlescolar.model.PersonalCedulaProfesional;
import com.idee.controlescolar.model.PersonalDocumento;
import com.idee.controlescolar.repository.MaestroRepository;
import com.idee.controlescolar.repository.PersonalCedulaProfesionalRepository;
import com.idee.controlescolar.repository.PersonalDocumentoRepository;
import com.idee.controlescolar.repository.PersonalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Mantiene alineados expediente en {@link Personal} (registro / staff) y {@link Maestro} (portal docente),
 * para documentos básicos, cédula profesional y fotografía.
 */
@Service
@RequiredArgsConstructor
public class DocenteExpedienteSyncService {

    private final PersonalRepository personalRepository;
    private final MaestroRepository maestroRepository;
    private final PersonalDocumentoRepository personalDocumentoRepository;
    private final PersonalCedulaProfesionalRepository personalCedulaProfesionalRepository;
    private final MaestroDocumentoExpedienteService maestroDocumentoExpedienteService;
    private final FileStorageService fileStorageService;

    public static String tipoMaestroParaPersonalBasico(PersonalDocumento.Tipo t) {
        if (t == null) {
            return null;
        }
        return switch (t) {
            case CURP_ARCHIVO -> DocumentoAlumno.TipoDocumento.CURP.name();
            case INE -> DocumentoAlumno.TipoDocumento.INE.name();
            case CSF -> DocumentoAlumno.TipoDocumento.CONSTANCIA_SITUACION_FISCAL.name();
        };
    }

    public static PersonalDocumento.Tipo tipoPersonalParaMaestroBasico(String maestroTipo) {
        if (maestroTipo == null || maestroTipo.isBlank()) {
            return null;
        }
        String u = maestroTipo.trim().toUpperCase();
        if (DocumentoAlumno.TipoDocumento.CURP.name().equals(u)) {
            return PersonalDocumento.Tipo.CURP_ARCHIVO;
        }
        if (DocumentoAlumno.TipoDocumento.INE.name().equals(u)) {
            return PersonalDocumento.Tipo.INE;
        }
        if (DocumentoAlumno.TipoDocumento.CONSTANCIA_SITUACION_FISCAL.name().equals(u)) {
            return PersonalDocumento.Tipo.CSF;
        }
        return null;
    }

    /**
     * Tras guardar ficha personal o documentos en staff: copia a expediente {@link Maestro} si el usuario es docente.
     */
    @Transactional
    public void propagarPersonalHaciaMaestro(Long personalId) {
        if (personalId == null) {
            return;
        }
        Optional<Personal> op = personalRepository.findByIdWithDocumentos(personalId);
        if (op.isEmpty()) {
            return;
        }
        Personal p = op.get();
        if (p.getUsuario() == null || p.getUsuario().getId() == null) {
            return;
        }
        Optional<Maestro> om = maestroRepository.findByUsuarioId(p.getUsuario().getId());
        if (om.isEmpty()) {
            return;
        }
        Maestro m = maestroRepository.findById(om.get().getId()).orElse(om.get());

        if (p.getDocumentos() != null) {
            for (PersonalDocumento pd : p.getDocumentos()) {
                if (pd == null || pd.getTipo() == null) {
                    continue;
                }
                byte[] data = pd.getData();
                if (data == null || data.length == 0) {
                    continue;
                }
                String mt = tipoMaestroParaPersonalBasico(pd.getTipo());
                if (mt == null) {
                    continue;
                }
                MaestroDocumento doc = maestroDocumentoExpedienteService.obtenerOCrearYVincular(m, mt);
                doc.setFilename(pd.getFilename() != null ? pd.getFilename() : (mt.toLowerCase() + ".pdf"));
                doc.setContentType(pd.getContentType());
                doc.setSizeBytes(pd.getSizeBytes() != null ? pd.getSizeBytes() : (long) data.length);
                doc.setData(data);
            }
        }

        List<PersonalCedulaProfesional> ceds = personalCedulaProfesionalRepository.findByPersonal_IdOrderByIdAsc(p.getId());
        boolean algunaCedulaConArchivo = false;
        for (PersonalCedulaProfesional c : ceds) {
            if (c != null && c.getData() != null && c.getData().length > 0) {
                MaestroDocumento doc = maestroDocumentoExpedienteService.obtenerOCrearYVincular(
                        m, DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL.name());
                doc.setFilename(c.getFilename() != null && !c.getFilename().isBlank() ? c.getFilename() : "cedula.pdf");
                doc.setContentType(c.getContentType());
                doc.setSizeBytes(c.getSizeBytes());
                doc.setData(c.getData());
                algunaCedulaConArchivo = true;
                break;
            }
        }
        if (!algunaCedulaConArchivo) {
            maestroDocumentoExpedienteService.limpiarContenido(m.getId(), DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL.name());
        }

        if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) {
            copiarFotoDesdeRutaHaciaMaestro(p.getFotoUrl(), m);
        }

        maestroRepository.save(m);
    }

    /**
     * Tras subir documento o foto desde el portal docente: refleja en ficha {@link Personal}.
     */
    @Transactional
    public void propagarMaestroHaciaPersonal(Long maestroId) {
        if (maestroId == null) {
            return;
        }
        Maestro m = maestroRepository.findById(maestroId).orElse(null);
        if (m == null || m.getUsuario() == null) {
            return;
        }
        Optional<Personal> op = personalRepository.findByUsuario_Id(m.getUsuario().getId());
        if (op.isEmpty()) {
            return;
        }
        Personal p = personalRepository.findByIdWithDocumentos(op.get().getId()).orElse(op.get());
        Long pid = p.getId();

        String[] basicos = {
                DocumentoAlumno.TipoDocumento.CURP.name(),
                DocumentoAlumno.TipoDocumento.INE.name(),
                DocumentoAlumno.TipoDocumento.CONSTANCIA_SITUACION_FISCAL.name()
        };
        for (String mt : basicos) {
            Optional<MaestroDocumento> omd = maestroDocumentoExpedienteService.buscar(maestroId, mt);
            if (omd.isEmpty() || !MaestroDocumentoExpedienteService.tieneContenido(omd.get())) {
                continue;
            }
            MaestroDocumento md = omd.get();
            PersonalDocumento.Tipo pt = tipoPersonalParaMaestroBasico(mt);
            if (pt == null) {
                continue;
            }
            PersonalDocumento d = personalDocumentoRepository.findByPersonal_IdAndTipo(pid, pt).orElseGet(() -> {
                PersonalDocumento n = new PersonalDocumento();
                n.setPersonal(p);
                n.setTipo(pt);
                return n;
            });
            d.setFilename(md.getFilename());
            d.setContentType(md.getContentType());
            d.setSizeBytes(md.getSizeBytes());
            d.setData(md.getData());
            personalDocumentoRepository.save(d);
        }

        maestroDocumentoExpedienteService.buscar(maestroId, DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL.name()).ifPresent(md -> {
            if (!MaestroDocumentoExpedienteService.tieneContenido(md)) {
                return;
            }
            List<PersonalCedulaProfesional> lista = personalCedulaProfesionalRepository.findByPersonal_IdOrderByIdAsc(pid);
            PersonalCedulaProfesional target = null;
            for (PersonalCedulaProfesional c : lista) {
                if (c != null) {
                    target = c;
                    break;
                }
            }
            if (target == null) {
                target = new PersonalCedulaProfesional();
                target.setPersonal(p);
                String num = m.getCedulaProfesional() != null && !m.getCedulaProfesional().isBlank()
                        ? m.getCedulaProfesional().trim() : "—";
                target.setNumero(num);
            }
            target.setFilename(md.getFilename() != null ? md.getFilename() : "cedula.pdf");
            target.setContentType(md.getContentType());
            target.setSizeBytes(md.getSizeBytes());
            target.setData(md.getData());
            personalCedulaProfesionalRepository.save(target);
        });

        if (m.getFotoUrl() != null && !m.getFotoUrl().isBlank()) {
            copiarFotoDesdeRutaHaciaPersonal(m.getFotoUrl(), p);
        }

        personalRepository.save(p);
    }

    private void copiarFotoDesdeRutaHaciaMaestro(String rutaPersonal, Maestro m) {
        try {
            Path path = Paths.get(rutaPersonal);
            if (!Files.isRegularFile(path)) {
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            String fn = path.getFileName() != null ? path.getFileName().toString() : "foto.jpg";
            String nueva = fileStorageService.storeMaestroBytes(m.getId(), bytes, "foto", fn);
            m.setFotoUrl(nueva);
        } catch (IOException ignored) {
            // conservar foto previa del maestro
        }
    }

    private void copiarFotoDesdeRutaHaciaPersonal(String rutaMaestro, Personal p) {
        try {
            Path path = Paths.get(rutaMaestro);
            if (!Files.isRegularFile(path)) {
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            String fn = path.getFileName() != null ? path.getFileName().toString() : "foto.jpg";
            String nueva = fileStorageService.storePersonalBytes(p.getId(), bytes, "foto", fn);
            p.setFotoUrl(nueva);
        } catch (IOException ignored) {
            // conservar foto previa en personal
        }
    }

    @Transactional
    public void limpiarEspejoMaestroPorDocumentoPersonal(Long personalId, PersonalDocumento.Tipo tipo) {
        if (personalId == null || tipo == null) {
            return;
        }
        personalRepository.findById(personalId).flatMap(per -> {
            if (per.getUsuario() == null) {
                return Optional.empty();
            }
            return maestroRepository.findByUsuarioId(per.getUsuario().getId());
        }).ifPresent(m -> {
            String mt = tipoMaestroParaPersonalBasico(tipo);
            if (mt != null) {
                maestroDocumentoExpedienteService.limpiarContenido(m.getId(), mt);
            }
        });
    }

    @Transactional
    public void limpiarEspejoPersonalPorDocumentoMaestro(Long maestroId, String tipoMaestroNorm) {
        if (maestroId == null || tipoMaestroNorm == null || tipoMaestroNorm.isBlank()) {
            return;
        }
        String u = tipoMaestroNorm.trim().toUpperCase();
        Maestro m = maestroRepository.findById(maestroId).orElse(null);
        if (m == null || m.getUsuario() == null) {
            return;
        }
        Optional<Personal> op = personalRepository.findByUsuario_Id(m.getUsuario().getId());
        if (op.isEmpty()) {
            return;
        }
        Long pid = op.get().getId();
        PersonalDocumento.Tipo pt = tipoPersonalParaMaestroBasico(u);
        if (pt != null) {
            personalDocumentoRepository.findByPersonal_IdAndTipo(pid, pt).ifPresent(personalDocumentoRepository::delete);
        }
        if (DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL.name().equals(u)) {
            List<PersonalCedulaProfesional> ceds = personalCedulaProfesionalRepository.findByPersonal_IdOrderByIdAsc(pid);
            if (!ceds.isEmpty()) {
                PersonalCedulaProfesional c = ceds.get(0);
                c.setData(null);
                c.setContentType(null);
                c.setSizeBytes(null);
                c.setFilename("");
                personalCedulaProfesionalRepository.save(c);
            }
        }
    }

    /**
     * Si en personal no hay archivo pero sí en expediente maestro, devuelve metadatos equivalentes para la UI de staff.
     */
    public Optional<PersonalDocumento> leerDocumentoBasicoDesdeMaestroEspejo(Long personalId, PersonalDocumento.Tipo tipo) {
        if (personalId == null || tipo == null) {
            return Optional.empty();
        }
        String mt = tipoMaestroParaPersonalBasico(tipo);
        if (mt == null) {
            return Optional.empty();
        }
        return personalRepository.findById(personalId).flatMap(per -> {
            if (per.getUsuario() == null) {
                return Optional.empty();
            }
            return maestroRepository.findByUsuarioId(per.getUsuario().getId());
        }).flatMap(mae -> maestroDocumentoExpedienteService.buscar(mae.getId(), mt))
                .filter(MaestroDocumentoExpedienteService::tieneContenido)
                .map(md -> {
                    PersonalDocumento d = new PersonalDocumento();
                    d.setTipo(tipo);
                    d.setFilename(md.getFilename());
                    d.setContentType(md.getContentType());
                    d.setSizeBytes(md.getSizeBytes());
                    d.setData(md.getData());
                    return d;
                });
    }

    /**
     * Para la lista de documentos básicos en staff: completa con lo que solo exista en expediente maestro.
     */
    public List<StaffSaveResponse.DocumentoBasicoMeta> fusionarListaDocumentosBasicosStaff(
            Long personalId, List<StaffSaveResponse.DocumentoBasicoMeta> base) {
        if (personalId == null || base == null) {
            return base == null ? List.of() : base;
        }
        Set<String> presentes = new HashSet<>();
        for (StaffSaveResponse.DocumentoBasicoMeta m : base) {
            if (m != null && m.getTipo() != null) {
                presentes.add(m.getTipo());
            }
        }
        Optional<Maestro> om = personalRepository.findById(personalId).flatMap(per -> {
            if (per.getUsuario() == null) {
                return Optional.empty();
            }
            return maestroRepository.findByUsuarioId(per.getUsuario().getId());
        });
        if (om.isEmpty()) {
            return base;
        }
        List<StaffSaveResponse.DocumentoBasicoMeta> out = new ArrayList<>(base);
        for (AlumnoDocumentoMeta meta : maestroDocumentoExpedienteService.listarMetadatos(om.get().getId())) {
            if (meta == null || !Boolean.TRUE.equals(meta.getEntregado())) {
                continue;
            }
            PersonalDocumento.Tipo pt = tipoPersonalParaMaestroBasico(meta.getTipo());
            if (pt == null) {
                continue;
            }
            if (presentes.contains(pt.name())) {
                continue;
            }
            out.add(new StaffSaveResponse.DocumentoBasicoMeta(pt.name(), meta.getFilename()));
        }
        return out;
    }
}
