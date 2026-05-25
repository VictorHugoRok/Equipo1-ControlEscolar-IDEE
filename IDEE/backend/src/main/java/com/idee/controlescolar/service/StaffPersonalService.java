package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.DatosComplementoAlumnoRol;
import com.idee.controlescolar.dto.AlumnoProgramaAsignadoDTO;
import com.idee.controlescolar.dto.PersonalStaffRequest;
import com.idee.controlescolar.dto.PersonalStaffRolesRequest;
import com.idee.controlescolar.dto.StaffListItemDto;
import com.idee.controlescolar.dto.StaffSaveResponse;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.AlumnoPrograma;
import com.idee.controlescolar.model.AlumnoProgramaId;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.PeriodoAcademico;
import com.idee.controlescolar.model.Personal;
import com.idee.controlescolar.model.PersonalCedulaProfesional;
import com.idee.controlescolar.model.PersonalDocumento;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.AlumnoProgramaRepository;
import com.idee.controlescolar.repository.CalificacionRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import com.idee.controlescolar.repository.HorarioBloqueRepository;
import com.idee.controlescolar.repository.MaestroRepository;
import com.idee.controlescolar.repository.PeriodoAcademicoRepository;
import com.idee.controlescolar.repository.PersonalCedulaProfesionalRepository;
import com.idee.controlescolar.repository.PersonalDocumentoRepository;
import com.idee.controlescolar.repository.PersonalRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.repository.TituloElectronicoRepository;
import com.idee.controlescolar.repository.UsuarioRepository;
import com.idee.controlescolar.util.ValidacionRfc;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffPersonalService {

    private static final String PASSWORD_DEFECTO_ACCESO = "idee1234";

    private final PersonalRepository personalRepository;
    private final PersonalDocumentoRepository personalDocumentoRepository;
    private final PersonalCedulaProfesionalRepository personalCedulaProfesionalRepository;
    private final MaestroRepository maestroRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlumnoRepository alumnoRepository;
    private final AlumnoProgramaRepository alumnoProgramaRepository;
    private final CalificacionRepository calificacionRepository;
    private final TituloElectronicoRepository tituloElectronicoRepository;
    private final GrupoRepository grupoRepository;
    private final HorarioBloqueRepository horarioBloqueRepository;
    private final ProgramaEducativoRepository programaEducativoRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final DocumentoAlumnoExpedienteService documentoAlumnoExpedienteService;
    private final DocenteExpedienteSyncService docenteExpedienteSyncService;

    @Transactional(readOnly = true)
    public List<StaffListItemDto> listarStaff() {
        List<StaffListItemDto> out = new ArrayList<>();
        List<Personal> fichasPersonal = personalRepository.findAll();

        for (Personal p : fichasPersonal) {
            out.add(mapearPersonalFicha(p));
        }

        Set<Long> usuariosConPersonal = fichasPersonal.stream()
                .map(Personal::getUsuario)
                .filter(u -> u != null && u.getId() != null)
                .map(Usuario::getId)
                .collect(Collectors.toSet());

        for (Maestro m : maestroRepository.findAll()) {
            if (m.getUsuario() == null || m.getUsuario().getId() == null) {
                continue;
            }
            if (usuariosConPersonal.contains(m.getUsuario().getId())) {
                continue;
            }
            out.add(mapearMaestroSoloLegacy(m));
        }

        Set<Long> usuariosYaListados = out.stream()
                .map(StaffListItemDto::getUsuarioId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Alumno al : alumnoRepository.findAll()) {
            Usuario ua = al.getUsuario();
            if (ua == null || ua.getId() == null) {
                continue;
            }
            if (usuariosYaListados.contains(ua.getId())) {
                continue;
            }
            out.add(mapearAlumnoSolo(al));
        }

        out.sort(Comparator.comparing(d -> (d.getApellidoPaterno() != null ? d.getApellidoPaterno() : "")
                + " " + (d.getApellidoMaterno() != null ? d.getApellidoMaterno() : "")
                + " " + (d.getNombre() != null ? d.getNombre() : "")));
        return out;
    }

    private StaffListItemDto mapearPersonalFicha(Personal p) {
        Usuario u = p.getUsuario();
        List<String> roles = u != null
                ? u.getRolesEfectivos().stream().map(Enum::name).toList()
                : List.of(p.getPuesto() != null ? p.getPuesto() : "");
        Long maestroId = null;
        Long alumnoId = null;
        Long programaId = null;
        String programaNombre = null;
        if (u != null) {
            maestroId = maestroRepository.findByUsuarioId(u.getId()).map(Maestro::getId).orElse(null);
            Alumno alumno = alumnoRepository.findByUsuarioId(u.getId()).orElse(null);
            alumnoId = alumno != null ? alumno.getId() : null;
            if (alumno != null && alumno.getPrograma() != null && alumno.getPrograma().getId() != null) {
                programaId = alumno.getPrograma().getId();
                programaNombre = alumno.getPrograma().getNombre();
            }
            if (programaId == null && u.getProgramasAsignados() != null && !u.getProgramasAsignados().isEmpty()) {
                ProgramaEducativo pr = u.getProgramasAsignados().iterator().next();
                if (pr != null && pr.getId() != null) {
                    programaId = pr.getId();
                    programaNombre = pr.getNombre();
                }
            }
        }
        return StaffListItemDto.builder()
                .personalId(p.getId())
                .maestroId(maestroId)
                .alumnoId(alumnoId)
                .usuarioId(u != null ? u.getId() : null)
                .curp(p.getCurp())
                .nombre(p.getNombre())
                .apellidoPaterno(p.getApellidoPaterno())
                .apellidoMaterno(p.getApellidoMaterno())
                .etiqueta(p.getEtiqueta())
                .correoInstitucional(p.getCorreoInstitucional())
                .correoPersonal(p.getCorreoPersonal())
                .telefono(p.getTelefono())
                .codigoPostal(p.getCodigoPostal())
                .nombreContactoEmergencia(p.getNombreContactoEmergencia())
                .telefonoContactoEmergencia(p.getTelefonoContactoEmergencia())
                .sexo(p.getSexo() != null ? p.getSexo().name() : null)
                .fechaNacimiento(p.getFechaNacimiento())
                .programaId(programaId)
                .programaNombre(programaNombre)
                .roles(roles)
                .activo(p.getActivo())
                .soloMaestroLegacy(false)
                .soloAlumnoSinPersonal(false)
                .build();
    }

    private StaffListItemDto mapearMaestroSoloLegacy(Maestro m) {
        Usuario u = m.getUsuario();
        List<String> roles = u != null
                ? u.getRolesEfectivos().stream().map(Enum::name).toList()
                : List.of("MAESTRO");
        return StaffListItemDto.builder()
                .personalId(null)
                .maestroId(m.getId())
                .alumnoId(null)
                .usuarioId(u != null ? u.getId() : null)
                .curp(m.getCurp())
                .nombre(m.getNombre())
                .apellidoPaterno(m.getApellidoPaterno())
                .apellidoMaterno(m.getApellidoMaterno())
                .etiqueta(m.getEtiqueta())
                .correoInstitucional(m.getCorreoInstitucional())
                .correoPersonal(m.getCorreoPersonal())
                .telefono(m.getTelefono())
                .codigoPostal(m.getCodigoPostal())
                .nombreContactoEmergencia(m.getNombreContactoEmergencia())
                .telefonoContactoEmergencia(m.getTelefonoContactoEmergencia())
                .sexo(null)
                .fechaNacimiento(null)
                .programaId(null)
                .programaNombre(null)
                .roles(roles)
                .activo(m.getActivo())
                .soloMaestroLegacy(true)
                .soloAlumnoSinPersonal(false)
                .build();
    }

    private StaffListItemDto mapearAlumnoSolo(Alumno a) {
        Usuario u = a.getUsuario();
        List<String> roles = u != null
                ? u.getRolesEfectivos().stream().map(Enum::name).toList()
                : List.of(Usuario.TipoUsuario.ALUMNO.name());
        Long programaId = null;
        String programaNombre = null;
        if (a.getPrograma() != null && a.getPrograma().getId() != null) {
            programaId = a.getPrograma().getId();
            programaNombre = a.getPrograma().getNombre();
        }
        return StaffListItemDto.builder()
                .personalId(null)
                .maestroId(null)
                .alumnoId(a.getId())
                .usuarioId(u != null ? u.getId() : null)
                .curp(a.getCurp())
                .nombre(a.getNombre())
                .apellidoPaterno(a.getApellidoPaterno())
                .apellidoMaterno(a.getApellidoMaterno())
                .etiqueta(null)
                .correoInstitucional(a.getCorreoInstitucional())
                .correoPersonal(a.getCorreoPersonal())
                .telefono(a.getTelefono())
                .codigoPostal(a.getCodigoPostal())
                .nombreContactoEmergencia(a.getNombreContactoEmergencia())
                .telefonoContactoEmergencia(a.getTelefonoContactoEmergencia())
                .sexo(a.getSexo() != null ? a.getSexo().name() : null)
                .fechaNacimiento(a.getFechaNacimiento())
                .programaId(programaId)
                .programaNombre(programaNombre)
                .roles(roles)
                // Estatus de matrícula es por programa; consideramos "activo" si tiene alguna asignación activa/temporal.
                .activo(a.getProgramasAsignados() != null && a.getProgramasAsignados().stream().anyMatch(ap ->
                        ap != null && (ap.getEstatusMatricula() == com.idee.controlescolar.model.AlumnoPrograma.EstatusMatriculaPrograma.ACTIVA
                                || ap.getEstatusMatricula() == com.idee.controlescolar.model.AlumnoPrograma.EstatusMatriculaPrograma.BAJA_TEMPORAL)))
                .soloMaestroLegacy(false)
                .soloAlumnoSinPersonal(true)
                .build();
    }

    @Transactional
    public Personal crearStaff(PersonalStaffRequest req) {
        return crearStaff(req, null, null, null, null);
    }

    @Transactional
    public Personal crearStaff(PersonalStaffRequest req,
                              MultipartFile docCurp,
                              MultipartFile docIne,
                              MultipartFile docCsf,
                              MultipartFile fotoPerfil) {
        validarAltaStaffBasica(req);
        if (req.getRoles() == null || req.getRoles().isEmpty()) {
            req.setRoles(List.of(Usuario.TipoUsuario.SIN_ROL.name()));
        }
        Set<Usuario.TipoUsuario> roles = parseRolesStaff(req.getRoles());

        String correoInst = req.getCorreoInstitucional().trim();
        if (usuarioRepository.existsByEmailIgnoreCase(correoInst)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo institucional ya está registrado");
        }
        if (personalRepository.existsByCurp(req.getCurp().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe personal con este CURP");
        }

        Usuario u = new Usuario();
        u.setEmail(correoInst);
        String pwAlta = resolverPasswordAlta(req);
        u.setPassword(passwordEncoder.encode(pwAlta));
        // Forzar cambio cuando se crea con contraseña genérica (por defecto o explícita)
        boolean esGenerica = (req.getPassword() == null || req.getPassword().isBlank())
                || PASSWORD_DEFECTO_ACCESO.equals(pwAlta);
        u.setMustChangePassword(esGenerica);
        u.setActivo(req.getActivo() == null || req.getActivo());
        u.aplicarRolesMultiples(roles);

        Personal p = mapearEntidadPersonal(req, null);
        p.setUsuario(u);
        u.setPersonal(p);

        if (roles.contains(Usuario.TipoUsuario.MAESTRO)
                && maestroRepository.existsByCurp(p.getCurp().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un docente registrado con este CURP");
        }

        u = usuarioRepository.save(u);
        p = personalRepository.findByUsuario_Id(u.getId()).orElse(p);

        guardarDocumentosPersonalesOpcionales(p, docCurp, docIne, docCsf);
        guardarFotoPerfilOpcional(p, fotoPerfil);
        sincronizarCedulasProfesionales(p, req, null);

        if (roles.contains(Usuario.TipoUsuario.MAESTRO)) {
            sincronizarMaestroDesdePersonal(p, u);
            docenteExpedienteSyncService.propagarPersonalHaciaMaestro(p.getId());
        }
        if (roles.contains(Usuario.TipoUsuario.ALUMNO)) {
            ensureAlumnoDesdePersonal(p, u, req.getDatosAlumno());
            // Si el alumno aún no tiene foto, heredarla desde personal (si existe)
            if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) {
                Optional<Alumno> oa = alumnoRepository.findByUsuarioId(u.getId());
                if (oa.isPresent()) {
                    Alumno a = oa.get();
                    if (a.getFotoUrl() == null || a.getFotoUrl().isBlank()) {
                        a.setFotoUrl(p.getFotoUrl());
                        alumnoRepository.save(a);
                    }
                }
            }
        }

        return personalRepository.findById(p.getId()).orElse(p);
    }

    @Transactional(readOnly = true)
    public List<StaffSaveResponse.DocumentoBasicoMeta> listarDocumentosBasicosPersonal(Long personalId) {
        if (personalId == null) {
            return List.of();
        }
        Personal p = personalRepository.findByIdWithDocumentos(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        List<StaffSaveResponse.DocumentoBasicoMeta> desdePersonal = (p.getDocumentos() == null ? List.<PersonalDocumento>of() : p.getDocumentos()).stream()
                .filter(d -> d != null && d.getTipo() != null)
                .filter(d -> d.getTipo() == PersonalDocumento.Tipo.CURP_ARCHIVO
                        || d.getTipo() == PersonalDocumento.Tipo.INE
                        || d.getTipo() == PersonalDocumento.Tipo.CSF)
                .filter(d -> d.getData() != null && d.getData().length > 0)
                .map(d -> new StaffSaveResponse.DocumentoBasicoMeta(d.getTipo().name(), d.getFilename()))
                .toList();
        return docenteExpedienteSyncService.fusionarListaDocumentosBasicosStaff(personalId, desdePersonal);
    }

    @Transactional(readOnly = true)
    public PersonalDocumento obtenerDocumentoBasico(Long personalId, PersonalDocumento.Tipo tipo) {
        if (personalId == null || tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetros inválidos");
        }
        Personal p = personalRepository.findByIdWithDocumentos(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        Optional<PersonalDocumento> directo = (p.getDocumentos() == null ? List.<PersonalDocumento>of() : p.getDocumentos()).stream()
                .filter(d -> d != null && d.getTipo() == tipo)
                .filter(d -> d.getData() != null && d.getData().length > 0)
                .findFirst();
        if (directo.isPresent()) {
            return directo.get();
        }
        return docenteExpedienteSyncService.leerDocumentoBasicoDesdeMaestroEspejo(personalId, tipo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado"));
    }

    @Transactional
    public void eliminarDocumentoBasico(Long personalId, PersonalDocumento.Tipo tipo) {
        if (personalId == null || tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetros inválidos");
        }
        Personal p = personalRepository.findByIdWithDocumentos(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        if (p.getDocumentos() == null) {
            return;
        }
        p.getDocumentos().removeIf(d -> d != null && d.getTipo() == tipo);
        personalRepository.save(p);
        docenteExpedienteSyncService.limpiarEspejoMaestroPorDocumentoPersonal(personalId, tipo);
    }

    private static String soloDigitosTelefono(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\D", "");
    }

    private String resolverPasswordAlta(PersonalStaffRequest req) {
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            String p = req.getPassword().trim();
            if (p.length() < 6) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
            }
            return p;
        }
        return PASSWORD_DEFECTO_ACCESO;
    }

    /**
     * Alta mínima: identificación, contacto, emergencia y contraseña opcional (si no, defecto del sistema).
     */
    private void validarAltaStaffBasica(PersonalStaffRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud vacía");
        }
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es requerido");
        }
        if (req.getApellidoPaterno() == null || req.getApellidoPaterno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El apellido paterno es requerido");
        }
        if (req.getApellidoMaterno() == null || req.getApellidoMaterno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El apellido materno es requerido");
        }
        if (req.getCorreoInstitucional() == null || req.getCorreoInstitucional().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo institucional es requerido");
        }
        if (req.getCurp() == null || req.getCurp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CURP es requerido");
        }
        String tel = soloDigitosTelefono(req.getTelefono());
        if (tel.length() != 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono es requerido (10 dígitos)");
        }
        req.setTelefono(tel);
        // Contacto y teléfono de emergencia: opcionales.
        // Si vienen, validar formato del teléfono; si no, permitir null/blank.
        if (req.getNombreContactoEmergencia() != null && req.getNombreContactoEmergencia().isBlank()) {
            req.setNombreContactoEmergencia(null);
        }
        String telE = soloDigitosTelefono(req.getTelefonoContactoEmergencia());
        if (!telE.isBlank()) {
            if (telE.length() != 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono de emergencia debe tener 10 dígitos");
            }
            req.setTelefonoContactoEmergencia(telE);
        } else {
            req.setTelefonoContactoEmergencia(null);
        }
        try {
            ValidacionRfc.validarFormatoOpcional(req.getRfc());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        normalizarCapturaTextoStaff(req);
    }

    /**
     * Normaliza captura:
     * - Nombres/apellidos en formato Título (Cada palabra inicia con mayúscula y el resto minúscula).
     * - RFC en mayúsculas.
     * - Recorta espacios (correos: solo trim, sin cambiar mayúsc./minúsc.).
     */
    private void normalizarCapturaTextoStaff(PersonalStaffRequest req) {
        if (req == null) {
            return;
        }
        // Utilidad local: "juan pÉRez lopez" -> "Juan Pérez Lopez"
        java.util.function.Function<String, String> titleWords = (raw) -> {
            if (raw == null) return null;
            String s = raw.trim().replaceAll("\\s+", " ");
            if (s.isEmpty()) return "";
            String[] parts = s.split(" ");
            StringBuilder out = new StringBuilder();
            for (String p : parts) {
                if (p == null || p.isBlank()) continue;
                String w = p.trim();
                if (w.isEmpty()) continue;
                String first = w.substring(0, 1).toUpperCase();
                String rest = w.length() > 1 ? w.substring(1).toLowerCase() : "";
                if (out.length() > 0) out.append(' ');
                out.append(first).append(rest);
            }
            return out.toString();
        };

        if (req.getCurp() != null) {
            req.setCurp(req.getCurp().trim());
        }
        if (req.getNombre() != null) {
            req.setNombre(titleWords.apply(req.getNombre()));
        }
        if (req.getApellidoPaterno() != null) {
            req.setApellidoPaterno(titleWords.apply(req.getApellidoPaterno()));
        }
        if (req.getApellidoMaterno() != null) {
            req.setApellidoMaterno(titleWords.apply(req.getApellidoMaterno()));
        }
        if (req.getEtiqueta() != null && !req.getEtiqueta().isBlank()) {
            req.setEtiqueta(req.getEtiqueta().trim());
        }
        if (req.getCorreoInstitucional() != null) {
            req.setCorreoInstitucional(req.getCorreoInstitucional().trim());
        }
        if (req.getCorreoPersonal() != null && !req.getCorreoPersonal().isBlank()) {
            req.setCorreoPersonal(req.getCorreoPersonal().trim());
        }
        if (req.getCodigoPostal() != null && !req.getCodigoPostal().isBlank()) {
            req.setCodigoPostal(req.getCodigoPostal().trim());
        }
        if (req.getDepartamento() != null && !req.getDepartamento().isBlank()) {
            req.setDepartamento(req.getDepartamento().trim());
        }
        if (req.getArea() != null && !req.getArea().isBlank()) {
            req.setArea(req.getArea().trim());
        }
        if (req.getRfc() != null && !req.getRfc().isBlank()) {
            req.setRfc(req.getRfc().trim().toUpperCase());
        }
        if (req.getRegimenFiscal() != null && !req.getRegimenFiscal().isBlank()) {
            req.setRegimenFiscal(req.getRegimenFiscal().trim());
        }
        if (req.getObservaciones() != null && !req.getObservaciones().isBlank()) {
            req.setObservaciones(req.getObservaciones().trim());
        }
        if (req.getNombreContactoEmergencia() != null) {
            req.setNombreContactoEmergencia(titleWords.apply(req.getNombreContactoEmergencia()));
        }
        if (req.getCedulaProfesional() != null && !req.getCedulaProfesional().isBlank()) {
            req.setCedulaProfesional(req.getCedulaProfesional().trim());
        }
        if (req.getCedulasProfesionales() != null) {
            for (PersonalStaffRequest.CedulaProfesionalLineaRequest line : req.getCedulasProfesionales()) {
                if (line != null && line.getNumero() != null && !line.getNumero().isBlank()) {
                    line.setNumero(line.getNumero().trim());
                }
                if (line != null && line.getEtiqueta() != null && !line.getEtiqueta().isBlank()) {
                    line.setEtiqueta(titleWords.apply(line.getEtiqueta()));
                }
            }
        }
        if (req.getPuesto() != null && !req.getPuesto().isBlank()) {
            req.setPuesto(req.getPuesto().trim());
        }
    }

    private void guardarDocumentosPersonalesOpcionales(Personal p, MultipartFile docCurp, MultipartFile docIne, MultipartFile docCsf) {
        if (p.getId() == null) {
            return;
        }
        boolean cambio = false;
        if (docCurp != null && !docCurp.isEmpty()) {
            upsertPersonalDocumento(p, PersonalDocumento.Tipo.CURP_ARCHIVO, docCurp);
            cambio = true;
        }
        if (docIne != null && !docIne.isEmpty()) {
            upsertPersonalDocumento(p, PersonalDocumento.Tipo.INE, docIne);
            cambio = true;
        }
        if (docCsf != null && !docCsf.isEmpty()) {
            upsertPersonalDocumento(p, PersonalDocumento.Tipo.CSF, docCsf);
            cambio = true;
        }
        if (cambio) {
            personalRepository.save(p);
        }
    }

    private void guardarFotoPerfilOpcional(Personal p, MultipartFile fotoPerfil) {
        if (p == null || p.getId() == null) {
            return;
        }
        if (fotoPerfil == null || fotoPerfil.isEmpty()) {
            return;
        }
        try {
            String url = fileStorageService.storePersonalFile(p.getId(), fotoPerfil, "foto");
            p.setFotoUrl(url);
            personalRepository.save(p);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo guardar la foto de perfil");
        }
    }

    @Transactional
    public void actualizarDocumentosAlumnoDesdePersonal(Long personalId,
                                                       List<String> deleteTipos,
                                                       List<MultipartFile> documentos,
                                                       List<String> documentosTipos) {
        if (personalId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personal inválido");
        }
        Personal p = personalRepository.findById(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        Usuario u = p.getUsuario();
        if (u == null || u.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El personal no tiene usuario vinculado");
        }
        Alumno alumno = alumnoRepository.findByUsuarioId(u.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe expediente de alumno para este usuario"));

        // Borrados pendientes (solo si NO se sube archivo nuevo para ese tipo)
        LinkedHashSet<String> del = new LinkedHashSet<>();
        if (deleteTipos != null) {
            for (String t : deleteTipos) {
                if (t != null && !t.isBlank()) del.add(t.trim().toUpperCase());
            }
        }
        if (!del.isEmpty()) {
            for (String tRaw : del) {
                DocumentoAlumno.TipoDocumento t;
                try {
                    t = DocumentoAlumno.TipoDocumento.valueOf(tRaw);
                } catch (Exception e) {
                    continue;
                }
                boolean hayNuevo = false;
                if (documentos != null && documentosTipos != null) {
                    for (int i = 0; i < documentos.size(); i++) {
                        String tt = documentosTipos.size() > i ? documentosTipos.get(i) : null;
                        if (tt != null && tt.trim().equalsIgnoreCase(t.name())) {
                            MultipartFile f = documentos.get(i);
                            if (f != null && !f.isEmpty()) {
                                hayNuevo = true;
                                break;
                            }
                        }
                    }
                }
                if (hayNuevo) continue;
                if (t == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                    continue;
                }
                documentoAlumnoExpedienteService.limpiarArchivo(alumno.getId(), t);
            }
        }

        // Subidas nuevas
        if (documentos != null && !documentos.isEmpty()) {
            for (int i = 0; i < documentos.size(); i++) {
                MultipartFile archivo = documentos.get(i);
                if (archivo == null || archivo.isEmpty()) continue;
                String tipoTexto = (documentosTipos != null && documentosTipos.size() > i) ? documentosTipos.get(i) : null;
                DocumentoAlumno.TipoDocumento tipoDocumento = DocumentoAlumno.TipoDocumento.OTRO;
                if (tipoTexto != null) {
                    try {
                        tipoDocumento = DocumentoAlumno.TipoDocumento.valueOf(tipoTexto.trim().toUpperCase());
                    } catch (Exception ignored) {
                        tipoDocumento = DocumentoAlumno.TipoDocumento.OTRO;
                    }
                }
                DocumentoAlumno.TipoDocumento tipoRes = tipoDocumento;
                int slotTitulo = 0;
                if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_PROFESIONAL) {
                    tipoRes = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
                    slotTitulo = 1;
                } else if (tipoDocumento == DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL) {
                    tipoRes = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
                    slotTitulo = 2;
                } else if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                    slotTitulo = 1;
                }
                try {
                    String prefijo;
                    DocumentoAlumno documento;
                    if (tipoRes == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                        documento = documentoAlumnoExpedienteService.obtenerOCrearTituloCedulaEnSlot(alumno, slotTitulo);
                        prefijo = "titulo_cedula_s" + slotTitulo;
                    } else {
                        documento = documentoAlumnoExpedienteService.obtenerOCrearYVincular(alumno, tipoRes);
                        prefijo = tipoRes.name().toLowerCase(Locale.ROOT);
                    }
                    String archivoUrl = fileStorageService.storeAlumnoFile(alumno.getId(), archivo, prefijo);
                    documento.setArchivoUrl(archivoUrl);
                    documento.setEntregado(true);
                    documento.setFechaRecepcion(LocalDate.now());
                } catch (IOException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo guardar un documento del estudiante");
                }
            }
        }

        alumnoRepository.save(alumno);
    }

    @Transactional
    public void actualizarDocumentoAlumnoRawDesdePersonal(Long personalId,
                                                          DocumentoAlumno.TipoDocumento tipoDocumento,
                                                          Integer slot,
                                                          String filename,
                                                          String etiquetaDocumento,
                                                          String numeroCedula,
                                                          byte[] bytes) {
        if (personalId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personal inválido");
        }
        if (tipoDocumento == null || tipoDocumento == DocumentoAlumno.TipoDocumento.OTRO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de documento inválido");
        }
        Personal p = personalRepository.findById(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        Usuario u = p.getUsuario();
        if (u == null || u.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El personal no tiene usuario vinculado");
        }
        Alumno alumno = alumnoRepository.findByUsuarioId(u.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe expediente de alumno para este usuario"));
        DocumentoAlumno.TipoDocumento tipoRes = tipoDocumento;
        int slotTitulo = 0;
        if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_PROFESIONAL) {
            tipoRes = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
            slotTitulo = slot != null ? slot : 1;
        } else if (tipoDocumento == DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL) {
            tipoRes = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
            slotTitulo = slot != null ? slot : 2;
        } else if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            if (slot == null || slot < 1 || slot > DocumentoAlumnoExpedienteService.TITULO_CEDULA_MAX_SLOTS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica slot 1 a 4 para título/cédula");
            }
            slotTitulo = slot;
        }
        try {
            DocumentoAlumno documento;
            String prefijoStorage;
            if (tipoRes == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                documento = documentoAlumnoExpedienteService.obtenerOCrearTituloCedulaEnSlot(alumno, slotTitulo);
                String etiquetaLimpia = headerText(etiquetaDocumento);
                if (etiquetaLimpia != null && !etiquetaLimpia.isBlank()) {
                    documento.setEtiquetaDocumento(etiquetaLimpia);
                }
                String numeroLimpio = headerText(numeroCedula);
                if (numeroLimpio != null && !numeroLimpio.isBlank()) {
                    documento.setNumeroCedula(numeroLimpio);
                }
                prefijoStorage = "titulo_cedula_s" + slotTitulo;
            } else {
                documento = documentoAlumnoExpedienteService.obtenerOCrearYVincular(alumno, tipoRes);
                prefijoStorage = tipoRes.name().toLowerCase(Locale.ROOT);
            }
            String safeFn = (filename != null && !filename.isBlank())
                    ? filename
                    : (tipoRes.name().toLowerCase(Locale.ROOT) + ".bin");
            String archivoUrl = fileStorageService.storeAlumnoBytes(
                    alumno.getId(),
                    bytes != null ? bytes : new byte[0],
                    prefijoStorage,
                    safeFn
            );
            documento.setArchivoUrl(archivoUrl);
            documento.setEntregado(true);
            documento.setFechaRecepcion(LocalDate.now());
            alumnoRepository.save(alumno);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo guardar un documento del estudiante");
        }
    }

    private void aplicarArchivoCedula(PersonalCedulaProfesional c, MultipartFile f) {
        try {
            c.setFilename(f.getOriginalFilename() != null ? f.getOriginalFilename() : "cedula.pdf");
            c.setContentType(f.getContentType());
            c.setSizeBytes(f.getSize());
            c.setData(f.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo de cédula profesional");
        }
    }

    /**
     * Sincroniza cédulas profesionales (varias por usuario). Archivos solo para filas nuevas (id null), en orden.
     */
    private void sincronizarCedulasProfesionales(Personal p, PersonalStaffRequest req, List<MultipartFile> cedulasArchivos) {
        if (req.getCedulasProfesionales() == null) {
            return;
        }
        List<MultipartFile> arch = cedulasArchivos != null ? cedulasArchivos : Collections.emptyList();
        List<PersonalStaffRequest.CedulaProfesionalLineaRequest> lines = req.getCedulasProfesionales();
        int lineasConNumero = 0;
        for (PersonalStaffRequest.CedulaProfesionalLineaRequest l : lines) {
            if (l == null) {
                continue;
            }
            String n = l.getNumero() != null ? l.getNumero().trim() : "";
            if (!n.isEmpty()) {
                lineasConNumero++;
            }
        }
        if (lineasConNumero > 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se permiten como máximo 8 cédulas profesionales");
        }
        List<PersonalCedulaProfesional> actuales = personalCedulaProfesionalRepository.findByPersonal_IdOrderByIdAsc(p.getId());
        Set<Long> idsPayload = lines.stream()
                .filter(Objects::nonNull)
                .map(PersonalStaffRequest.CedulaProfesionalLineaRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        for (PersonalCedulaProfesional c : new ArrayList<>(actuales)) {
            if (!idsPayload.contains(c.getId())) {
                personalCedulaProfesionalRepository.delete(c);
            }
        }
        int idxArch = 0;
        for (PersonalStaffRequest.CedulaProfesionalLineaRequest line : lines) {
            if (line == null) {
                continue;
            }
            String num = line.getNumero() != null ? line.getNumero().trim() : "";
            if (num.isEmpty()) {
                continue;
            }
            String etiqueta = line.getEtiqueta() != null && !line.getEtiqueta().isBlank() ? line.getEtiqueta().trim() : null;
            if (line.getId() != null) {
                personalCedulaProfesionalRepository.findById(line.getId()).ifPresent(c -> {
                    if (c.getPersonal().getId().equals(p.getId())) {
                        c.setNumero(num);
                        c.setEtiqueta(etiqueta);
                        personalCedulaProfesionalRepository.save(c);
                    }
                });
            } else {
                MultipartFile mf = idxArch < arch.size() ? arch.get(idxArch++) : null;
                PersonalCedulaProfesional c = new PersonalCedulaProfesional();
                c.setPersonal(p);
                c.setNumero(num);
                c.setEtiqueta(etiqueta);
                if (mf != null && !mf.isEmpty()) {
                    aplicarArchivoCedula(c, mf);
                } else {
                    c.setFilename("(sin archivo)");
                    c.setSizeBytes(0L);
                    c.setData(new byte[0]);
                }
                personalCedulaProfesionalRepository.save(c);
            }
        }
    }

    private void upsertPersonalDocumento(Personal p, PersonalDocumento.Tipo tipo, MultipartFile file) {
        try {
            if (p == null || p.getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personal inválido para guardar documento");
            }
            if (tipo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de documento inválido");
            }

            PersonalDocumento d = personalDocumentoRepository
                    .findByPersonal_IdAndTipo(p.getId(), tipo)
                    .orElseGet(() -> {
                        PersonalDocumento n = new PersonalDocumento();
                        n.setPersonal(p);
                        n.setTipo(tipo);
                        return n;
                    });

            String fn = file.getOriginalFilename();
            if (fn == null || fn.isBlank()) {
                fn = tipo.name();
            }
            d.setFilename(fn);
            d.setContentType(file.getContentType());
            d.setSizeBytes(file.getSize());
            d.setData(file.getBytes());
            personalDocumentoRepository.save(d);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer un archivo adjunto");
        }
    }

    @Transactional
    public Personal actualizarStaff(Long personalId, PersonalStaffRequest req) {
        return ejecutarActualizacionStaff(personalId, req, null, null, null, null, null);
    }

    @Transactional
    public Personal actualizarStaffConArchivos(Long personalId, PersonalStaffRequest req,
            MultipartFile docCurp,
            MultipartFile docIne,
            MultipartFile docCsf,
            MultipartFile fotoPerfil,
            List<MultipartFile> cedulasArchivos) {
        return ejecutarActualizacionStaff(personalId, req, docCurp, docIne, docCsf, fotoPerfil, cedulasArchivos);
    }

    private Personal ejecutarActualizacionStaff(Long personalId, PersonalStaffRequest req,
            MultipartFile docCurp,
            MultipartFile docIne,
            MultipartFile docCsf,
            MultipartFile fotoPerfil,
            List<MultipartFile> cedulasArchivos) {
        Personal p = personalRepository.findById(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        validarSolicitud(req, personalId);
        boolean actualizaRoles = req.getRoles() != null && !req.getRoles().isEmpty();

        if (req.getCurp() != null && !req.getCurp().isBlank()
                && personalRepository.existsByCurpAndIdNot(req.getCurp().trim(), personalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro personal con este CURP");
        }

        String correoInst = req.getCorreoInstitucional().trim();
        Usuario u = p.getUsuario();
        if (u == null) {
            if (usuarioRepository.existsByEmailIgnoreCase(correoInst)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo institucional ya está registrado");
            }
            u = new Usuario();
            u.setEmail(correoInst);
            u.setPassword(passwordEncoder.encode(PASSWORD_DEFECTO_ACCESO));
            u.setMustChangePassword(true);
            u.setActivo(true);
            p.setUsuario(u);
            u.setPersonal(p);
            List<String> rolesInicial = actualizaRoles
                    ? req.getRoles()
                    : List.of(Usuario.TipoUsuario.SIN_ROL.name());
            u.aplicarRolesMultiples(parseRolesStaff(rolesInicial));
            u = usuarioRepository.save(u);
        } else {
            if (!correoInst.equalsIgnoreCase(u.getEmail()) && usuarioRepository.existsByEmailIgnoreCase(correoInst)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo institucional ya está registrado");
            }
            u.setEmail(correoInst);
            u.setActivo(req.getActivo() == null || req.getActivo());
            // Si un admin envía password (por ejemplo al restablecer), forzar cambio en el siguiente login
            if (req.getPassword() != null && !req.getPassword().isBlank()) {
                String pwr = req.getPassword().trim();
                if (pwr.length() < 6) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
                }
                u.setPassword(passwordEncoder.encode(pwr));
                u.setMustChangePassword(true);
            }
            if (actualizaRoles) {
                u.aplicarRolesMultiples(parseRolesStaff(req.getRoles()));
            }
            usuarioRepository.save(u);
        }

        if (actualizaRoles || req.getProgramaCoordinadoId() != null) {
            sincronizarProgramasAsignadosParaCoord(u, u.getRolesEfectivos(), req.getProgramaCoordinadoId());
            usuarioRepository.save(u);
        }

        aplicarCamposPersonal(p, req, u);
        p = personalRepository.save(p);

        guardarDocumentosPersonalesOpcionales(p, docCurp, docIne, docCsf);
        guardarFotoPerfilOpcional(p, fotoPerfil);
        sincronizarCedulasProfesionales(p, req, cedulasArchivos);

        Set<Usuario.TipoUsuario> efectivos = u.getRolesEfectivos();
        if (efectivos.contains(Usuario.TipoUsuario.MAESTRO)) {
            String c = p.getCurp() != null ? p.getCurp().trim() : "";
            Optional<Maestro> om = maestroRepository.findByUsuarioId(u.getId());
            Long mid = om.map(Maestro::getId).orElse(null);
            if (mid != null) {
                if (maestroRepository.existsByCurpAndIdNot(c, mid)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro docente con este CURP");
                }
            } else if (maestroRepository.existsByCurp(c)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un docente con este CURP");
            }
        }

        if (efectivos.contains(Usuario.TipoUsuario.MAESTRO)) {
            sincronizarMaestroDesdePersonal(p, u);
            docenteExpedienteSyncService.propagarPersonalHaciaMaestro(p.getId());
        } else {
            quitarPerfilMaestroSiSePuede(u.getId());
        }

        if (efectivos.contains(Usuario.TipoUsuario.ALUMNO)) {
            ensureAlumnoDesdePersonal(p, u, req.getDatosAlumno());
            // Sincroniza foto al expediente de alumno (si aplica)
            if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) {
                Optional<Alumno> oa = alumnoRepository.findByUsuarioId(u.getId());
                if (oa.isPresent()) {
                    Alumno a = oa.get();
                    if (a.getFotoUrl() == null || a.getFotoUrl().isBlank()) {
                        a.setFotoUrl(p.getFotoUrl());
                        alumnoRepository.save(a);
                    }
                }
            }
        } else {
            quitarAlumnoSiSePuede(u.getId());
        }

        return p;
    }

    /**
     * Actualiza solo los roles del usuario vinculado a la ficha de personal (desde la lista).
     */
    @Transactional
    public Personal actualizarRolesStaff(Long personalId, PersonalStaffRolesRequest req) {
        if (req == null || req.getRoles() == null || req.getRoles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar al menos un rol");
        }
        Personal p = personalRepository.findById(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        Usuario u = p.getUsuario();
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La ficha no tiene usuario asociado");
        }
        Set<Usuario.TipoUsuario> roles = parseRolesStaff(req.getRoles());
        // Evita INSERT incompleto: referencia transitoria/huérfana en Usuario.alumno al hacer save con cascade ALL
        u.setAlumno(alumnoRepository.findByUsuarioId(u.getId()).orElse(null));
        u.aplicarRolesMultiples(roles);
        sincronizarProgramasAsignadosParaCoord(u, roles, req.getProgramaCoordinadoId());
        usuarioRepository.save(u);
        p.setPuesto(u.getTipoUsuario().name());
        p = personalRepository.save(p);

        if (roles.contains(Usuario.TipoUsuario.MAESTRO)) {
            String c = p.getCurp() != null ? p.getCurp().trim() : "";
            Optional<Maestro> om = maestroRepository.findByUsuarioId(u.getId());
            Long mid = om.map(Maestro::getId).orElse(null);
            if (mid != null) {
                if (maestroRepository.existsByCurpAndIdNot(c, mid)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro docente con este CURP");
                }
            } else if (maestroRepository.existsByCurp(c)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un docente con este CURP");
            }
            sincronizarMaestroDesdePersonal(p, u);
            docenteExpedienteSyncService.propagarPersonalHaciaMaestro(p.getId());
        } else {
            quitarPerfilMaestroSiSePuede(u.getId());
        }

        if (roles.contains(Usuario.TipoUsuario.ALUMNO)) {
            ensureAlumnoDesdePersonal(p, u, req.getDatosAlumno());
        } else {
            quitarAlumnoSiSePuede(u.getId());
        }
        return p;
    }

    /**
     * Actualiza roles desde la lista cuando la fila usa usuarioId (expediente solo alumno, sin personal).
     */
    @Transactional
    public Usuario actualizarRolesPorUsuarioId(Long usuarioId, PersonalStaffRolesRequest req) {
        if (req == null || req.getRoles() == null || req.getRoles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar al menos un rol");
        }
        Optional<Personal> op = personalRepository.findByUsuario_Id(usuarioId);
        if (op.isPresent()) {
            actualizarRolesStaff(op.get().getId(), req);
            return usuarioRepository.findById(usuarioId).orElseThrow();
        }
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (alumnoRepository.findByUsuarioId(usuarioId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario no tiene expediente de estudiante ni ficha de personal");
        }
        Set<Usuario.TipoUsuario> roles = parseRolesStaff(req.getRoles());
        u.aplicarRolesMultiples(roles);
        sincronizarProgramasAsignadosParaCoord(u, roles, req.getProgramaCoordinadoId());
        usuarioRepository.save(u);
        return u;
    }

    private void sincronizarProgramasAsignadosParaCoord(Usuario u, Set<Usuario.TipoUsuario> roles, Long programaCoordinadoId) {
        if (u == null) return;
        boolean esCoord = roles != null && roles.contains(Usuario.TipoUsuario.COORDINADOR_ACADEMICO);
        if (!esCoord) {
            if (u.getProgramasAsignados() != null && !u.getProgramasAsignados().isEmpty()) {
                u.getProgramasAsignados().clear();
            }
            return;
        }
        if (programaCoordinadoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para asignar el rol Coordinador académico debe seleccionar el programa educativo que coordinará.");
        }
        ProgramaEducativo prog = programaEducativoRepository.findById(programaCoordinadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programa educativo no encontrado"));
        if (u.getProgramasAsignados() == null) {
            u.setProgramasAsignados(new java.util.HashSet<>());
        } else {
            u.getProgramasAsignados().clear();
        }
        u.getProgramasAsignados().add(prog);
    }

    @Transactional
    public void eliminarStaff(Long personalId) {
        Personal p = personalRepository.findById(personalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        Usuario u = p.getUsuario();
        if (u != null) {
            maestroRepository.findByUsuarioId(u.getId()).ifPresent(m -> {
                if (!grupoRepository.findByMaestroId(m.getId()).isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "No se puede eliminar: el docente tiene grupos como titular");
                }
                var bloques = horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                        m.getId(), com.idee.controlescolar.model.HorarioBloque.EstatusHorario.ACTIVO);
                if (!bloques.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "No se puede eliminar: el docente tiene horarios activos");
                }
                m.setUsuario(null);
                maestroRepository.delete(m);
            });
        }
        p.setUsuario(null);
        personalRepository.delete(p);
        if (u != null) {
            u.setPersonal(null);
            u.setMaestro(null);
            usuarioRepository.delete(u);
        }
    }

    private void validarSolicitud(PersonalStaffRequest req, Long idExcluirCurp) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud vacía");
        }
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es requerido");
        }
        if (req.getApellidoPaterno() == null || req.getApellidoPaterno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El apellido paterno es requerido");
        }
        if (req.getApellidoMaterno() == null || req.getApellidoMaterno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El apellido materno es requerido");
        }
        if (req.getCorreoInstitucional() == null || req.getCorreoInstitucional().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo institucional es requerido");
        }
        if (req.getCurp() == null || req.getCurp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CURP es requerido");
        }

        // En actualizaciones, permitir guardados parciales (por ejemplo desde vista de expediente),
        // sin obligar a re-capturar teléfono y datos de emergencia si ya existían.
        if (req.getTelefono() != null && !req.getTelefono().isBlank()) {
            String telEd = soloDigitosTelefono(req.getTelefono());
            if (telEd.length() != 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono debe tener 10 dígitos");
            }
            req.setTelefono(telEd);
        }
        if (req.getNombreContactoEmergencia() != null && !req.getNombreContactoEmergencia().isBlank()) {
            // normaliza en normalizarCapturaTextoStaff
        }
        if (req.getTelefonoContactoEmergencia() != null && !req.getTelefonoContactoEmergencia().isBlank()) {
            String telEmergEd = soloDigitosTelefono(req.getTelefonoContactoEmergencia());
            if (telEmergEd.length() != 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono de emergencia debe tener 10 dígitos");
            }
            req.setTelefonoContactoEmergencia(telEmergEd);
        }
        try {
            ValidacionRfc.validarFormatoOpcional(req.getRfc());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        normalizarCapturaTextoStaff(req);
    }

    private Set<Usuario.TipoUsuario> parseRolesStaff(List<String> raw) {
        Set<Usuario.TipoUsuario> set = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            Usuario.TipoUsuario t = Usuario.TipoUsuario.valueOf(s.trim().toUpperCase());
            set.add(t);
        }
        if (set.size() > 1 && set.contains(Usuario.TipoUsuario.SIN_ROL)) {
            set.remove(Usuario.TipoUsuario.SIN_ROL);
        }
        if (set.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos un rol válido");
        }
        return set;
    }

    @Transactional(readOnly = true)
    public PersonalCedulaProfesional obtenerCedulaProfesionalArchivo(Long personalId, Long cedulaId) {
        PersonalCedulaProfesional c = personalCedulaProfesionalRepository.findById(cedulaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cédula no encontrada"));
        if (!c.getPersonal().getId().equals(personalId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cédula no encontrada");
        }
        return c;
    }

    @Transactional
    public void eliminarCedulaProfesionalArchivo(Long personalId, Long cedulaId) {
        PersonalCedulaProfesional c = personalCedulaProfesionalRepository.findById(cedulaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cédula no encontrada"));
        if (!c.getPersonal().getId().equals(personalId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cédula no encontrada");
        }
        c.setData(null);
        c.setContentType(null);
        c.setSizeBytes(null);
        // mantener NOT NULL pero indicar "sin archivo" con string vacío (UI lo interpreta como no cargado)
        c.setFilename("");
        personalCedulaProfesionalRepository.save(c);
        docenteExpedienteSyncService.propagarPersonalHaciaMaestro(personalId);
    }

    private Personal mapearEntidadPersonal(PersonalStaffRequest req, Long ignoreId) {
        Personal p = new Personal();
        aplicarCamposPersonal(p, req, null);
        return p;
    }

    private String resolverPrimeraCedula(PersonalStaffRequest req) {
        if (req.getCedulasProfesionales() != null) {
            for (PersonalStaffRequest.CedulaProfesionalLineaRequest l : req.getCedulasProfesionales()) {
                if (l != null && l.getNumero() != null && !l.getNumero().isBlank()) {
                    return l.getNumero().trim();
                }
            }
        }
        if (req.getCedulaProfesional() != null && !req.getCedulaProfesional().isBlank()) {
            return req.getCedulaProfesional().trim();
        }
        return null;
    }

    private void aplicarCamposPersonal(Personal p, PersonalStaffRequest req, Usuario usuarioRefParaPuesto) {
        String puesto;
        if (req.getPuesto() != null && !req.getPuesto().isBlank()) {
            puesto = req.getPuesto().trim();
        } else if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            Usuario orden = new Usuario();
            orden.aplicarRolesMultiples(parseRolesStaff(req.getRoles()));
            puesto = orden.getTipoUsuario().name();
        } else if (usuarioRefParaPuesto != null && usuarioRefParaPuesto.getTipoUsuario() != null) {
            puesto = usuarioRefParaPuesto.getTipoUsuario().name();
        } else {
            puesto = Usuario.TipoUsuario.SIN_ROL.name();
        }

        p.setCurp(req.getCurp() != null ? req.getCurp().trim() : "");
        p.setNombre(req.getNombre().trim());
        p.setApellidoPaterno(req.getApellidoPaterno().trim());
        p.setApellidoMaterno(req.getApellidoMaterno().trim());
        p.setEtiqueta(req.getEtiqueta());
        p.setCorreoInstitucional(req.getCorreoInstitucional().trim());
        p.setCorreoPersonal(req.getCorreoPersonal() != null && !req.getCorreoPersonal().isBlank() ? req.getCorreoPersonal().trim() : null);
        if (req.getTelefono() != null && !req.getTelefono().isBlank()) {
            p.setTelefono(req.getTelefono().trim());
        }
        p.setCodigoPostal(req.getCodigoPostal() != null ? req.getCodigoPostal().trim() : null);
        if (req.getSexo() != null && !req.getSexo().isBlank()) {
            p.setSexo(parseSexoAlumnoRol(req.getSexo()));
        }
        if (req.getFechaNacimiento() != null) {
            p.setFechaNacimiento(req.getFechaNacimiento());
        }
        p.setGradoAcademico(req.getGradoAcademico());
        p.setCedulaProfesional(resolverPrimeraCedula(req));
        p.setPuesto(puesto);
        p.setDepartamento(req.getDepartamento() != null ? req.getDepartamento().trim() : null);
        p.setArea(req.getArea() != null ? req.getArea().trim() : null);
        p.setTipoMaestro(req.getTipoMaestro());
        p.setRfc(req.getRfc() != null ? req.getRfc().trim() : null);
        p.setRegimenFiscal(req.getRegimenFiscal() != null ? req.getRegimenFiscal().trim() : null);
        p.setFechaAlta(req.getFechaAlta());
        p.setActivo(req.getActivo() == null || req.getActivo());
        p.setObservaciones(req.getObservaciones() != null ? req.getObservaciones().trim() : null);
        if (req.getNombreContactoEmergencia() != null && !req.getNombreContactoEmergencia().isBlank()) {
            p.setNombreContactoEmergencia(req.getNombreContactoEmergencia().trim());
        }
        if (req.getTelefonoContactoEmergencia() != null && !req.getTelefonoContactoEmergencia().isBlank()) {
            p.setTelefonoContactoEmergencia(req.getTelefonoContactoEmergencia().trim());
        }
    }

    private void sincronizarMaestroDesdePersonal(Personal p, Usuario u) {
        Maestro m = maestroRepository.findByUsuarioId(u.getId()).orElse(new Maestro());
        m.setUsuario(u);
        u.setMaestro(m);
        m.setCurp(p.getCurp());
        m.setNombre(p.getNombre());
        m.setApellidoPaterno(p.getApellidoPaterno());
        m.setApellidoMaterno(p.getApellidoMaterno() != null ? p.getApellidoMaterno() : "");
        m.setEtiqueta(p.getEtiqueta());
        m.setCorreoInstitucional(p.getCorreoInstitucional());
        m.setCorreoPersonal(p.getCorreoPersonal());
        m.setTelefono(p.getTelefono());
        m.setCodigoPostal(p.getCodigoPostal());
        m.setGradoAcademico(p.getGradoAcademico() != null
                ? Maestro.GradoAcademico.valueOf(p.getGradoAcademico().name()) : null);
        m.setCedulaProfesional(p.getCedulaProfesional());
        m.setArea(p.getArea());
        m.setRfc(p.getRfc());
        m.setRegimenFiscal(p.getRegimenFiscal());
        m.setTipoMaestro(p.getTipoMaestro());
        m.setFechaAlta(p.getFechaAlta());
        m.setActivo(p.getActivo() != null && p.getActivo());
        m.setObservaciones(p.getObservaciones());
        m.setNombreContactoEmergencia(p.getNombreContactoEmergencia());
        m.setTelefonoContactoEmergencia(p.getTelefonoContactoEmergencia());
        maestroRepository.save(m);
        usuarioRepository.save(u);
    }

    private void quitarPerfilMaestroSiSePuede(Long usuarioId) {
        Optional<Maestro> opt = maestroRepository.findByUsuarioId(usuarioId);
        if (opt.isEmpty()) {
            return;
        }
        Maestro m = opt.get();
        if (!grupoRepository.findByMaestroId(m.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede quitar el rol Docente: tiene grupos como titular. Reasigne los grupos primero.");
        }
        var bloques = horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                m.getId(), com.idee.controlescolar.model.HorarioBloque.EstatusHorario.ACTIVO);
        if (!bloques.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede quitar el rol Docente: tiene horarios activos.");
        }
        Usuario u = m.getUsuario();
        m.setUsuario(null);
        if (u != null) {
            u.setMaestro(null);
            usuarioRepository.save(u);
        }
        maestroRepository.delete(m);
    }

    private void ensureAlumnoDesdePersonal(Personal p, Usuario u, DatosComplementoAlumnoRol datos) {
        if (p.getId() != null) {
            p = personalRepository.findById(p.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado"));
        }
        String nombreAlu = textoObligatorioAlumnoDesdePersonal(p.getNombre(), "Nombre");
        String apPatAlu = textoObligatorioAlumnoDesdePersonal(p.getApellidoPaterno(), "Apellido paterno");
        String apMatAlu = textoApellidoMaternoAlumno(p.getApellidoMaterno());
        String curpAlu = p.getCurp() != null ? p.getCurp().trim() : "";
        if (curpAlu.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede asignar el rol Estudiante sin CURP registrado en la ficha de personal.");
        }

        Optional<Alumno> oa = alumnoRepository.findByUsuarioId(u.getId());
        if (oa.isPresent()) {
            Alumno a = oa.get();
            aplicarIdentidadAlumnoDesdePersonal(a, p, nombreAlu, apPatAlu, apMatAlu, curpAlu);
            if (datos != null) {
                aplicarComplementoAlumnoOpcional(a, datos);
            }
            alumnoRepository.save(a);
            return;
        }
        if (datos == null
                || datos.getMatricula() == null || datos.getMatricula().isBlank()
                || ((datos.getProgramasAsignados() == null || datos.getProgramasAsignados().isEmpty()) && datos.getProgramaId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para asignar el rol Estudiante debe completar matrícula y programa educativo en el formulario de datos de estudiante.");
        }
        String mat = datos.getMatricula().trim();
        if (alumnoRepository.existsByMatricula(mat)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un alumno con la matrícula indicada.");
        }
        if (p.getSexo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede asignar el rol Estudiante: falta «Género» en la ficha de personal.");
        }
        if (p.getFechaNacimiento() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede asignar el rol Estudiante: falta «Fecha de nacimiento» en la ficha de personal.");
        }

        Alumno a = new Alumno();
        a.setUsuario(u);
        u.setAlumno(a);
        a.setMatricula(mat);
        a.setNombre(nombreAlu);
        a.setApellidoPaterno(apPatAlu);
        a.setApellidoMaterno(apMatAlu);
        a.setCurp(curpAlu);
        a.setCorreoInstitucional(p.getCorreoInstitucional());
        a.setTelefono(p.getTelefono());
        a.setCodigoPostal(p.getCodigoPostal());
        a.setNombreContactoEmergencia(p.getNombreContactoEmergencia());
        a.setTelefonoContactoEmergencia(p.getTelefonoContactoEmergencia());
        a.setSexo(p.getSexo());
        a.setFechaNacimiento(p.getFechaNacimiento());
        a.setPeriodoCursando(1);
        Alumno guardado = alumnoRepository.save(a);
        // Asignación de programas
        if (datos.getProgramasAsignados() != null && !datos.getProgramasAsignados().isEmpty()) {
            sincronizarProgramasAsignadosAlumno(guardado, datos.getProgramasAsignados());
        } else if (datos.getProgramaId() != null) {
            AlumnoProgramaAsignadoDTO legacy = new AlumnoProgramaAsignadoDTO();
            legacy.setProgramaId(datos.getProgramaId());
            legacy.setPeriodoAcademicoIngresoId(datos.getPeriodoAcademicoIngresoId());
            legacy.setEstatusMatricula(datos.getEstatusMatricula());
            sincronizarProgramasAsignadosAlumno(guardado, List.of(legacy));
        }
        usuarioRepository.save(u);
    }

    private void aplicarIdentidadAlumnoDesdePersonal(
            Alumno a, Personal p, String nombreAlu, String apPatAlu, String apMatAlu, String curpAlu) {
        a.setNombre(nombreAlu);
        a.setApellidoPaterno(apPatAlu);
        a.setApellidoMaterno(apMatAlu);
        a.setCurp(curpAlu);
        a.setCorreoInstitucional(p.getCorreoInstitucional());
        a.setTelefono(p.getTelefono());
        a.setCodigoPostal(p.getCodigoPostal());
        if (p.getNombreContactoEmergencia() != null) {
            a.setNombreContactoEmergencia(p.getNombreContactoEmergencia());
        }
        if (p.getTelefonoContactoEmergencia() != null) {
            a.setTelefonoContactoEmergencia(p.getTelefonoContactoEmergencia());
        }
        if (p.getSexo() != null) {
            a.setSexo(p.getSexo());
        }
        if (p.getFechaNacimiento() != null) {
            a.setFechaNacimiento(p.getFechaNacimiento());
        }
    }

    private void aplicarComplementoAlumnoOpcional(Alumno a, DatosComplementoAlumnoRol datos) {
        if (datos.getMatricula() != null && !datos.getMatricula().isBlank()) {
            String m = datos.getMatricula().trim();
            if (!m.equals(a.getMatricula()) && alumnoRepository.existsByMatricula(m)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro alumno con la matrícula indicada.");
            }
            a.setMatricula(m);
        }
        // Si el frontend envía programasAsignados (aunque sea vacío), se considera "lista final deseada".
        // Esto evita que queden programas "fantasma" cuando se edita y se eliminan filas.
        if (datos.getProgramasAsignados() != null) {
            sincronizarProgramasAsignadosAlumno(a, datos.getProgramasAsignados());
        } else if (datos.getProgramaId() != null) {
            AlumnoProgramaAsignadoDTO legacy = new AlumnoProgramaAsignadoDTO();
            legacy.setProgramaId(datos.getProgramaId());
            legacy.setPeriodoAcademicoIngresoId(datos.getPeriodoAcademicoIngresoId());
            legacy.setEstatusMatricula(datos.getEstatusMatricula());
            sincronizarProgramasAsignadosAlumno(a, List.of(legacy));
        }
        if (datos.getSexo() != null && !datos.getSexo().isBlank()) {
            a.setSexo(parseSexoAlumnoRol(datos.getSexo()));
        }
        if (datos.getFechaNacimiento() != null && !datos.getFechaNacimiento().isBlank()) {
            a.setFechaNacimiento(parseFechaNacimientoAlumnoOpcional(datos.getFechaNacimiento()));
        }
        if (datos.getPeriodoAcademicoIngresoId() != null) {
            PeriodoAcademico pa = periodoAcademicoRepository.findById(datos.getPeriodoAcademicoIngresoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo de ingreso no encontrado."));
            a.setPeriodoAcademico(pa);
        }
    }

    private void sincronizarProgramasAsignadosAlumno(Alumno alumno, List<AlumnoProgramaAsignadoDTO> items) {
        if (alumno == null || alumno.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alumno inválido para asignar programas.");
        }
        if (items == null) {
            return;
        }
        if (items.isEmpty()) {
            if (alumno.getProgramasAsignados() != null) {
                alumno.getProgramasAsignados().clear();
            }
            alumnoProgramaRepository.flush();
            List<AlumnoPrograma> restantesVac = alumnoProgramaRepository.findByAlumno_Id(alumno.getId());
            if (restantesVac != null && !restantesVac.isEmpty()) {
                alumnoProgramaRepository.deleteAll(restantesVac);
                alumnoProgramaRepository.flush();
            }
            alinearColeccionProgramasEnAlumno(alumno);
            return;
        }
        // Normalizar: 1 por programaId
        var porPrograma = new java.util.LinkedHashMap<Long, AlumnoProgramaAsignadoDTO>();
        for (AlumnoProgramaAsignadoDTO it : items) {
            if (it == null || it.getProgramaId() == null) continue;
            porPrograma.put(it.getProgramaId(), it);
        }
        if (porPrograma.isEmpty()) {
            // No omitir en silencio: deja programas viejos en BD y parece que «no se puede quitar» un programa.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las asignaciones de programa no son válidas: cada fila debe incluir un programaId.");
        }

        List<AlumnoPrograma> existentes = alumnoProgramaRepository.findByAlumno_Id(alumno.getId());
        var existentesPorPid = new java.util.HashMap<Long, AlumnoPrograma>();
        for (AlumnoPrograma ap : (existentes == null ? List.<AlumnoPrograma>of() : existentes)) {
            if (ap != null && ap.getPrograma() != null && ap.getPrograma().getId() != null) {
                existentesPorPid.put(ap.getPrograma().getId(), ap);
            }
        }

        // Upsert
        List<AlumnoPrograma> guardar = new ArrayList<>();
        for (Long pid : porPrograma.keySet()) {
            AlumnoProgramaAsignadoDTO it = porPrograma.get(pid);
            ProgramaEducativo programa = programaEducativoRepository.findById(pid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Programa educativo no encontrado."));
            AlumnoPrograma ap = existentesPorPid.get(pid);
            if (ap == null) {
                ap = new AlumnoPrograma();
                ap.setAlumno(alumno);
                ap.setPrograma(programa);
                ap.setId(new AlumnoProgramaId(alumno.getId(), programa.getId()));
                ap.setPeriodoCursando(1);
            } else {
                ap.setPrograma(programa);
            }
            if (it.getPeriodoAcademicoIngresoId() != null) {
                PeriodoAcademico pa = periodoAcademicoRepository.findById(it.getPeriodoAcademicoIngresoId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo de ingreso no encontrado."));
                ap.setPeriodoIngreso(pa);
            }
            String est = it.getEstatusMatricula() != null ? it.getEstatusMatricula().trim() : "";
            if (!est.isBlank()) {
                try {
                    ap.setEstatusMatricula(AlumnoPrograma.EstatusMatriculaPrograma.valueOf(est.toUpperCase(Locale.ROOT)));
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Estatus de matrícula inválido. Use ACTIVA, BAJA_TEMPORAL, BAJA_DEFINITIVA o EGRESADO.");
                }
            }
            guardar.add(ap);
        }
        if (!guardar.isEmpty()) {
            alumnoProgramaRepository.saveAll(guardar);
        }

        // No usar DELETE JPQL masivo aquí: deja entidades AlumnoPrograma aún gestionadas (p. ej. con auditoría
        // @LastModifiedDate) y el siguiente flush lanza ObjectOptimisticLockingFailureException al actualizar
        // filas ya borradas. Quitar solo vía colección del padre (orphanRemoval) y, si hiciera falta, deleteAll.
        Set<Long> mantenerIds = porPrograma.keySet();
        if (alumno.getProgramasAsignados() != null) {
            alumno.getProgramasAsignados().removeIf(ap ->
                    ap != null && ap.getPrograma() != null && ap.getPrograma().getId() != null
                            && !mantenerIds.contains(ap.getPrograma().getId()));
        }
        alumnoProgramaRepository.flush();

        List<AlumnoPrograma> resto = alumnoProgramaRepository.findByAlumno_Id(alumno.getId());
        List<AlumnoPrograma> sobran = new ArrayList<>();
        for (AlumnoPrograma ap : (resto == null ? List.<AlumnoPrograma>of() : resto)) {
            if (ap != null && ap.getPrograma() != null && ap.getPrograma().getId() != null
                    && !mantenerIds.contains(ap.getPrograma().getId())) {
                sobran.add(ap);
            }
        }
        if (!sobran.isEmpty()) {
            if (alumno.getProgramasAsignados() != null) {
                for (AlumnoPrograma ap : sobran) {
                    alumno.getProgramasAsignados().remove(ap);
                }
            }
            alumnoProgramaRepository.deleteAll(sobran);
            alumnoProgramaRepository.flush();
        }

        // Alinear la colección en memoria con la BD. Con orphanRemoval, un save(Alumno) sin esto puede dejar
        // filas huérfanas o desincronizar el contexto de persistencia respecto a alumno_programa.
        alinearColeccionProgramasEnAlumno(alumno);
    }

    private void alinearColeccionProgramasEnAlumno(Alumno alumno) {
        if (alumno == null || alumno.getId() == null) {
            return;
        }
        alumnoProgramaRepository.flush();
        List<AlumnoPrograma> enBd = alumnoProgramaRepository.findByAlumno_Id(alumno.getId());
        if (enBd == null) {
            enBd = List.of();
        }
        Set<AlumnoProgramaId> idsBd = enBd.stream()
                .map(AlumnoPrograma::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (alumno.getProgramasAsignados() == null) {
            alumno.setProgramasAsignados(new LinkedHashSet<>());
        }
        alumno.getProgramasAsignados().removeIf(ap -> ap.getId() == null || !idsBd.contains(ap.getId()));
        for (AlumnoPrograma ap : enBd) {
            boolean ya = alumno.getProgramasAsignados().stream()
                    .anyMatch(x -> x.getId() != null && x.getId().equals(ap.getId()));
            if (!ya) {
                ap.setAlumno(alumno);
                alumno.getProgramasAsignados().add(ap);
            }
        }
    }

    private static Alumno.Sexo parseSexoAlumnoRol(String raw) {
        try {
            return Alumno.Sexo.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sexo inválido. Use MASCULINO o FEMENINO.");
        }
    }

    private static Alumno.EstatusMatricula parseEstatusMatriculaAlumnoRol(String raw) {
        try {
            return Alumno.EstatusMatricula.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estatus de matrícula inválido. Use ACTIVA, BAJA_TEMPORAL, BAJA_DEFINITIVA o EGRESADO.");
        }
    }

    private static LocalDate parseFechaNacimientoAlumnoOpcional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha de nacimiento inválida. Use el formato AAAA-MM-DD.");
        }
    }

    /** Evita NULL en columnas NOT NULL de alumnos al copiar desde personal. */
    private static String textoApellidoMaternoAlumno(String raw) {
        if (raw == null || raw.isBlank()) {
            return " ";
        }
        String t = raw.trim();
        return t.isEmpty() ? " " : t;
    }

    private static String textoObligatorioAlumnoDesdePersonal(String raw, String campo) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede asignar el rol Estudiante: falta «" + campo + "» en la ficha de personal. Complete el expediente y vuelva a intentar.");
        }
        return raw.trim();
    }

    private static String headerText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.contains("%")) {
            try {
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // Si el header no venía URL-encoded válido, usarlo tal cual.
            }
        }
        return value.trim();
    }

    private void quitarAlumnoSiSePuede(Long usuarioId) {
        Optional<Alumno> oa = alumnoRepository.findByUsuarioId(usuarioId);
        if (oa.isEmpty()) {
            return;
        }
        Alumno a = oa.get();
        Long aid = a.getId();
        if (!calificacionRepository.findByAlumnoId(aid).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede quitar el rol Alumno: hay calificaciones registradas.");
        }
        if (!grupoRepository.findByAlumnos_Id(aid).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede quitar el rol Alumno: está inscrito en grupos.");
        }
        if (tituloElectronicoRepository.existsByAlumnoId(aid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede quitar el rol Alumno: existen títulos electrónicos asociados.");
        }
        Usuario u = a.getUsuario();
        a.setUsuario(null);
        if (u != null) {
            u.setAlumno(null);
            usuarioRepository.save(u);
        }
        alumnoRepository.delete(a);
    }

    /**
     * Convierte un docente solo en tabla {@code maestros} a ficha unificada en {@code personal}.
     */
    @Transactional
    public Personal migrarMaestroAFicha(Long maestroId, PersonalStaffRequest req) {
        Maestro m = maestroRepository.findById(maestroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maestro no encontrado"));
        Usuario u = m.getUsuario();
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El maestro no tiene usuario vinculado");
        }
        if (personalRepository.findByUsuario_Id(u.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este usuario ya tiene ficha de personal");
        }
        validarSolicitud(req, null);
        if (req.getRoles() == null || req.getRoles().isEmpty()) {
            req.setRoles(List.of(Usuario.TipoUsuario.MAESTRO.name()));
        }
        Set<Usuario.TipoUsuario> roles = parseRolesStaff(req.getRoles());

        Personal p = new Personal();
        aplicarCamposPersonal(p, req, u);
        p.setUsuario(u);
        u.setPersonal(p);
        u.aplicarRolesMultiples(roles);
        personalRepository.save(p);
        usuarioRepository.save(u);
        sincronizarMaestroDesdePersonal(p, u);
        return personalRepository.findByUsuario_Id(u.getId()).orElse(p);
    }
}
