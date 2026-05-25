package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.EvaluacionDocenteCrearFormularioRequest;
import com.idee.controlescolar.dto.EvaluacionDocenteResponderRequest;
import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluacionDocenteService {

    /** Texto del informe institucional visible para el docente (Evaluación Académica). */
    public static final int INFORME_PARA_DOCENTE_MAX_CARACTERES = 8000;

    private final EvaluacionDocenteFormularioRepository formularioRepository;
    private final EvaluacionDocenteRespuestaRepository respuestaRepository;
    private final EvaluacionDocenteItemRepository itemRepository;
    private final MaestroRepository maestroRepository;
    private final GrupoRepository grupoRepository;
    private final HorarioBloqueRepository horarioBloqueRepository;
    private final CalificacionRepository calificacionRepository;
    private final AlumnoRepository alumnoRepository;
    private final GestionAcademicaEstadoService gestionAcademicaEstadoService;

    public boolean esFormularioVigente(EvaluacionDocenteFormulario f, LocalDate hoy) {
        if (f == null || !Boolean.TRUE.equals(f.getActivo())) {
            return false;
        }
        if (f.getFechaInicio() != null && hoy.isBefore(f.getFechaInicio())) {
            return false;
        }
        return f.getFechaFin() == null || !hoy.isAfter(f.getFechaFin());
    }

    public Optional<EvaluacionDocenteFormulario> obtenerFormularioVigenteParaAlumno() {
        return formularioRepository.findByActivoTrueOrderByFechaCreacionDesc().stream()
                .filter(f -> f != null && f.getTipo() == EvaluacionDocenteFormulario.TipoEvaluacion.POR_ALUMNOS)
                .findFirst();
    }

    public Optional<EvaluacionDocenteFormulario> obtenerFormularioVigenteParaSecretaria() {
        LocalDate hoy = LocalDate.now();
        return formularioRepository.findByActivoTrueOrderByFechaCreacionDesc().stream()
                .filter(f -> f != null && f.getTipo() == EvaluacionDocenteFormulario.TipoEvaluacion.POR_SECRETARIA_ACADEMICA)
                .filter(f -> esFormularioVigente(f, hoy))
                .findFirst();
    }

    public Optional<EvaluacionDocenteFormulario> obtenerFormularioVigenteParaAutoevaluacion() {
        return formularioRepository.findByActivoTrueOrderByFechaCreacionDesc().stream()
                .filter(f -> f != null && f.getTipo() == EvaluacionDocenteFormulario.TipoEvaluacion.AUTOEVALUACION)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarFormulariosResumen() {
        return formularioRepository.findAllByOrderByFechaCreacionDesc().stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("titulo", f.getTitulo());
            m.put("tipo", f.getTipo() != null ? f.getTipo().name() : null);
            m.put("activo", f.getActivo());
            m.put("fechaCreacion", f.getFechaCreacion());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerFormularioDetalle(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id es obligatorio.");
        }
        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(id)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId());
        m.put("titulo", f.getTitulo());
        m.put("descripcion", f.getDescripcion());
        m.put("tipo", f.getTipo() != null ? f.getTipo().name() : null);
        m.put("activo", f.getActivo());
        m.put("fechaCreacion", f.getFechaCreacion());
        m.put("fechaActualizacion", f.getFechaActualizacion());
        m.put("preguntas", f.getPreguntas().stream()
                .sorted(Comparator.comparing(EvaluacionDocentePregunta::getOrden, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(EvaluacionDocentePregunta::getId))
                .map(p -> Map.of(
                        "id", p.getId(),
                        "texto", p.getTexto(),
                        "orden", p.getOrden(),
                        "bloque", p.getBloque(),
                        "tipo", p.getTipo() != null ? p.getTipo().name() : null,
                        "opciones", p.getOpciones()
                ))
                .collect(Collectors.toList()));
        return m;
    }

    @Transactional
    public EvaluacionDocenteFormulario actualizarFormularioCompleto(Long id, EvaluacionDocenteCrearFormularioRequest req) {
        if (id == null) {
            throw new IllegalArgumentException("id es obligatorio.");
        }
        if (req == null || req.getTitulo() == null || req.getTitulo().isBlank()) {
            throw new IllegalArgumentException("El título del formulario es obligatorio.");
        }
        boolean hasBloques = req.getBloques() != null && !req.getBloques().isEmpty();
        boolean hasLegacy = req.getPreguntas() != null && !req.getPreguntas().isEmpty();
        if (!hasBloques && !hasLegacy) {
            throw new IllegalArgumentException("Debe incluir al menos una pregunta.");
        }

        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(id)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        if (respuestaRepository.existsByFormulario_Id(id)) {
            throw new IllegalArgumentException(
                    "Este formulario ya tiene respuestas registradas. Para conservar el historial, crea una nueva version y desactiva la anterior.");
        }
        f.setTitulo(req.getTitulo().trim());
        f.setDescripcion(req.getDescripcion() != null ? req.getDescripcion().trim() : null);
        f.setActivo(req.getActivo() != null ? req.getActivo() : true);
        // Vigencia por calendario no aplica: la disponibilidad la marca el horario de cada módulo.
        f.setFechaInicio(null);
        f.setFechaFin(null);
        // Tipo
        try {
            if (req.getTipo() != null && !req.getTipo().isBlank()) {
                f.setTipo(EvaluacionDocenteFormulario.TipoEvaluacion.valueOf(req.getTipo().trim().toUpperCase()));
            }
        } catch (Exception ignored) {
            f.setTipo(EvaluacionDocenteFormulario.TipoEvaluacion.POR_ALUMNOS);
        }

        // Reemplazar preguntas
        f.getPreguntas().clear();
        int ordenGlobal = 0;
        if (hasBloques) {
            List<EvaluacionDocenteCrearFormularioRequest.Bloque> bloques = req.getBloques();
            for (int bi = 0; bi < bloques.size(); bi++) {
                var b = bloques.get(bi);
                if (b == null) continue;
                String bloqueTitulo = (b.getTitulo() == null || b.getTitulo().isBlank()) ? ("Bloque " + (bi + 1)) : b.getTitulo().trim();
                List<EvaluacionDocenteCrearFormularioRequest.Pregunta> ps = b.getPreguntas();
                if (ps == null) continue;
                for (int pi = 0; pi < ps.size(); pi++) {
                    var q = ps.get(pi);
                    if (q == null || q.getTexto() == null || q.getTexto().isBlank()) continue;
                    EvaluacionDocentePregunta p = new EvaluacionDocentePregunta();
                    p.setFormulario(f);
                    p.setBloque(bloqueTitulo);
                    p.setTexto(q.getTexto().trim());
                    p.setOrden(q.getOrden() != null ? q.getOrden() : ordenGlobal++);
                    try {
                        if (q.getTipo() != null && !q.getTipo().isBlank()) {
                            p.setTipo(EvaluacionDocentePregunta.TipoPregunta.valueOf(q.getTipo().trim().toUpperCase()));
                        }
                    } catch (Exception ignored) {
                        p.setTipo(EvaluacionDocentePregunta.TipoPregunta.LIKERT_5);
                    }
                    if (p.getTipo() == EvaluacionDocentePregunta.TipoPregunta.OPCION_MULTIPLE) {
                        List<String> ops = q.getOpciones() != null ? q.getOpciones() : List.of();
                        String joined = ops.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).collect(Collectors.joining("\n"));
                        if (joined.isBlank()) {
                            throw new IllegalArgumentException("Las preguntas de opción múltiple deben incluir opciones.");
                        }
                        p.setOpciones(joined);
                    } else {
                        p.setOpciones(null);
                    }
                    f.getPreguntas().add(p);
                }
            }
        } else {
            int orden = 0;
            for (String texto : req.getPreguntas()) {
                if (texto == null || texto.isBlank()) continue;
                EvaluacionDocentePregunta p = new EvaluacionDocentePregunta();
                p.setFormulario(f);
                p.setBloque("Bloque 1");
                p.setTipo(EvaluacionDocentePregunta.TipoPregunta.LIKERT_5);
                p.setTexto(texto.trim());
                p.setOrden(orden++);
                f.getPreguntas().add(p);
            }
        }
        if (f.getPreguntas().isEmpty()) {
            throw new IllegalArgumentException("Las preguntas no pueden quedar vacías.");
        }
        f.setFechaActualizacion(LocalDateTime.now());
        return formularioRepository.save(f);
    }

    @Transactional(readOnly = true)
    public List<Maestro> listarMaestrosDeGruposDelAlumno(Long alumnoId) {
        List<Grupo> grupos = grupoRepository.findByAlumnos_Id(alumnoId);
        Map<Long, Maestro> porId = new LinkedHashMap<>();
        for (Grupo g : grupos) {
            if (g.getMaestro() != null && g.getMaestro().getId() != null) {
                porId.putIfAbsent(g.getMaestro().getId(), g.getMaestro());
            }
        }
        return new ArrayList<>(porId.values());
    }

    @Transactional
    public EvaluacionDocenteFormulario crearFormulario(EvaluacionDocenteCrearFormularioRequest req) {
        if (req == null || req.getTitulo() == null || req.getTitulo().isBlank()) {
            throw new IllegalArgumentException("El título del formulario es obligatorio.");
        }
        boolean hasBloques = req.getBloques() != null && !req.getBloques().isEmpty();
        boolean hasLegacy = req.getPreguntas() != null && !req.getPreguntas().isEmpty();
        if (!hasBloques && !hasLegacy) {
            throw new IllegalArgumentException("Debe incluir al menos una pregunta.");
        }
        EvaluacionDocenteFormulario f = new EvaluacionDocenteFormulario();
        f.setTitulo(req.getTitulo().trim());
        f.setDescripcion(req.getDescripcion() != null ? req.getDescripcion().trim() : null);
        f.setActivo(req.getActivo() != null ? req.getActivo() : true);
        f.setFechaInicio(null);
        f.setFechaFin(null);
        f.setFechaCreacion(LocalDateTime.now());
        // Tipo (por ahora usaremos POR_ALUMNOS como default)
        try {
            if (req.getTipo() != null && !req.getTipo().isBlank()) {
                f.setTipo(EvaluacionDocenteFormulario.TipoEvaluacion.valueOf(req.getTipo().trim().toUpperCase()));
            }
        } catch (Exception ignored) {
            f.setTipo(EvaluacionDocenteFormulario.TipoEvaluacion.POR_ALUMNOS);
        }

        int ordenGlobal = 0;
        if (hasBloques) {
            List<EvaluacionDocenteCrearFormularioRequest.Bloque> bloques = req.getBloques();
            for (int bi = 0; bi < bloques.size(); bi++) {
                var b = bloques.get(bi);
                if (b == null) continue;
                String bloqueTitulo = (b.getTitulo() == null || b.getTitulo().isBlank()) ? ("Bloque " + (bi + 1)) : b.getTitulo().trim();
                List<EvaluacionDocenteCrearFormularioRequest.Pregunta> ps = b.getPreguntas();
                if (ps == null) continue;
                for (int pi = 0; pi < ps.size(); pi++) {
                    var q = ps.get(pi);
                    if (q == null || q.getTexto() == null || q.getTexto().isBlank()) continue;
                    EvaluacionDocentePregunta p = new EvaluacionDocentePregunta();
                    p.setFormulario(f);
                    p.setBloque(bloqueTitulo);
                    p.setTexto(q.getTexto().trim());
                    p.setOrden(q.getOrden() != null ? q.getOrden() : ordenGlobal++);
                    try {
                        if (q.getTipo() != null && !q.getTipo().isBlank()) {
                            p.setTipo(EvaluacionDocentePregunta.TipoPregunta.valueOf(q.getTipo().trim().toUpperCase()));
                        }
                    } catch (Exception ignored) {
                        p.setTipo(EvaluacionDocentePregunta.TipoPregunta.LIKERT_5);
                    }
                    if (p.getTipo() == EvaluacionDocentePregunta.TipoPregunta.OPCION_MULTIPLE) {
                        List<String> ops = q.getOpciones() != null ? q.getOpciones() : List.of();
                        String joined = ops.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).collect(Collectors.joining("\n"));
                        if (joined.isBlank()) {
                            throw new IllegalArgumentException("Las preguntas de opción múltiple deben incluir opciones.");
                        }
                        p.setOpciones(joined);
                    } else {
                        p.setOpciones(null);
                    }
                    f.getPreguntas().add(p);
                }
            }
        } else {
            int orden = 0;
            for (String texto : req.getPreguntas()) {
                if (texto == null || texto.isBlank()) {
                    continue;
                }
                EvaluacionDocentePregunta p = new EvaluacionDocentePregunta();
                p.setFormulario(f);
                p.setBloque("Bloque 1");
                p.setTipo(EvaluacionDocentePregunta.TipoPregunta.LIKERT_5);
                p.setTexto(texto.trim());
                p.setOrden(orden++);
                f.getPreguntas().add(p);
            }
        }
        if (f.getPreguntas().isEmpty()) {
            throw new IllegalArgumentException("Las preguntas no pueden quedar vacías.");
        }
        return formularioRepository.save(f);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> contextoParaAlumno(Alumno alumno) {
        Optional<EvaluacionDocenteFormulario> opt = obtenerFormularioVigenteParaAlumno();
        if (opt.isEmpty()) {
            return Map.of("formulario", null, "yaRespondio", false, "maestros", List.of(),
                    "obligaciones", List.of(), "pendientesEvaluacion", 0L);
        }
        EvaluacionDocenteFormulario raw = opt.get();
        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(raw.getId())
                .orElse(raw);
        List<Map<String, Object>> obligaciones = construirObligacionesAlumno(alumno, f.getId());
        long pendientes = obligaciones.stream().filter(o -> Boolean.TRUE.equals(o.get("puedeResponder"))).count();
        boolean sinPendientes = obligaciones.stream().noneMatch(o -> Boolean.TRUE.equals(o.get("puedeResponder")));
        List<Map<String, Object>> maestros = obligaciones.stream()
                .filter(o -> o.get("maestroId") != null)
                .collect(Collectors.toMap(
                        o -> (Long) o.get("maestroId"),
                        o -> o,
                        (a, b) -> a,
                        LinkedHashMap::new))
                .values().stream()
                .map(o -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", o.get("maestroId"));
                    row.put("nombreCompleto", o.get("nombreCompleto"));
                    return row;
                })
                .collect(Collectors.toList());
        Map<String, Object> formMap = new HashMap<>();
        formMap.put("id", f.getId());
        formMap.put("titulo", f.getTitulo());
        formMap.put("descripcion", f.getDescripcion());
        formMap.put("tipo", f.getTipo() != null ? f.getTipo().name() : null);
        List<Map<String, Object>> preguntas = f.getPreguntas().stream()
                .sorted(Comparator.comparing(EvaluacionDocentePregunta::getOrden, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(EvaluacionDocentePregunta::getId))
                .map(p -> {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", p.getId());
                    pm.put("texto", p.getTexto());
                    pm.put("orden", p.getOrden());
                    pm.put("bloque", p.getBloque());
                    pm.put("tipo", p.getTipo() != null ? p.getTipo().name() : null);
                    pm.put("opciones", p.getOpciones());
                    return pm;
                })
                .collect(Collectors.toList());
        formMap.put("preguntas", preguntas);
        Map<String, Object> out = new HashMap<>();
        out.put("formulario", formMap);
        out.put("obligaciones", obligaciones);
        out.put("pendientesEvaluacion", pendientes);
        out.put("sinObligacionesPendientes", sinPendientes);
        out.put("yaRespondio", sinPendientes && !obligaciones.isEmpty());
        out.put("maestros", maestros);
        return out;
    }

    @Transactional(readOnly = true)
    public List<Maestro> listarMaestrosConCalificacionConfirmadaDelAlumno(Long alumnoId) {
        if (alumnoId == null) return List.of();
        Set<Long> ids = calificacionRepository.findDistinctMaestroIdsConCalificacionConfirmadaDelAlumno(alumnoId);
        if (ids == null || ids.isEmpty()) return List.of();
        return maestroRepository.findAllById(ids).stream()
                .filter(m -> m != null && m.getId() != null)
                .sorted(Comparator.comparing(Maestro::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean alumnoYaRespondioEvaluacionDocenteVigente(Long alumnoId) {
        if (alumnoId == null) {
            return true;
        }
        Optional<EvaluacionDocenteFormulario> opt = obtenerFormularioVigenteParaAlumno();
        if (opt.isEmpty()) {
            return true;
        }
        Optional<Alumno> a = alumnoRepository.findById(alumnoId);
        if (a.isEmpty()) {
            return true;
        }
        List<Map<String, Object>> obl = construirObligacionesAlumno(a.get(), opt.get().getId());
        return obl.stream().noneMatch(o -> Boolean.TRUE.equals(o.get("puedeResponder")));
    }

    /**
     * Clases (horario canónico) que secretaría/coordinación puede evaluar: una tarjeta por docente + grupo + materia.
     */
    private List<HorarioBloque> listarHorariosEvaluacionAcademicaSecretaria(Usuario evaluadorUsuario) {
        List<Maestro> maestros = listarMaestrosParaEvaluacionAcademica(evaluadorUsuario);
        Map<Long, HorarioBloque> byId = new LinkedHashMap<>();
        for (Maestro m : maestros) {
            if (m == null || m.getId() == null) {
                continue;
            }
            for (HorarioBloque h : horariosCanonicoMaestro(m)) {
                if (h != null && h.getId() != null
                        && gestionAcademicaEstadoService.validarNuevaEvaluacion(h.getPeriodoAcademico()) == null) {
                    byId.putIfAbsent(h.getId(), h);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> contextoParaSecretaria(Usuario evaluadorUsuario) {
        Optional<EvaluacionDocenteFormulario> opt = obtenerFormularioVigenteParaSecretaria();
        if (opt.isEmpty()) {
            return Map.of("formulario", null, "yaRespondio", false, "maestros", List.of(), "clases", List.of(),
                    "pendientesEvaluacionDocentes", 0L);
        }
        EvaluacionDocenteFormulario raw = opt.get();
        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(raw.getId()).orElse(raw);

        List<HorarioBloque> horarios = listarHorariosEvaluacionAcademicaSecretaria(evaluadorUsuario);
        horarios.sort(Comparator.comparing(HorarioBloque::getId, Comparator.nullsLast(Long::compareTo)));

        List<Map<String, Object>> clases = new ArrayList<>();
        for (HorarioBloque h : horarios) {
            Maestro m = h.getMaestro();
            if (m == null || m.getId() == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("horarioBloqueId", h.getId());
            row.put("maestroId", m.getId());
            row.put("nombreCompleto", nombreCompletoMaestro(m));
            Grupo g = h.getGrupoEntity();
            row.put("grupoNombre", g != null ? g.getNombre() : (h.getGrupoNombre() != null ? h.getGrupoNombre() : "—"));
            Asignatura a = h.getAsignatura();
            row.put("asignaturaNombre", a != null ? a.getNombre() : "—");
            if (h.getPeriodoAcademico() != null) {
                row.put("periodoCodigo", h.getPeriodoAcademico().getCodigo());
            } else {
                row.put("periodoCodigo", h.getCicloEscolar());
            }
            // Una evaluación académica por formulario, docente y clase (horario/módulo).
            List<EvaluacionDocenteRespuesta> evClase = respuestaRepository.findEvaluacionesAcademicasPorFormularioMaestroYHorario(
                    f.getId(), m.getId(), h.getId());
            boolean yaEv = !evClase.isEmpty();
            row.put("yaEvaluado", yaEv);
            if (yaEv) {
                EvaluacionDocenteRespuesta r0 = evClase.get(0);
                row.put("fechaVisita", r0.getFechaVisita());
                row.put("respuestaAcademicaId", r0.getId());
                row.put("nombreEvaluador", etiquetaEvaluadorAcademico(r0.getEvaluadorUsuario()));
                row.put("informeParaDocente", r0.getInformeParaDocente());
            }
            clases.add(row);
        }

        long pendientes = clases.stream().filter(mm -> !Boolean.TRUE.equals(mm.get("yaEvaluado"))).count();

        Map<String, Object> formMap = new HashMap<>();
        formMap.put("id", f.getId());
        formMap.put("titulo", f.getTitulo());
        formMap.put("descripcion", f.getDescripcion());
        formMap.put("tipo", f.getTipo() != null ? f.getTipo().name() : null);
        List<Map<String, Object>> preguntas = f.getPreguntas().stream()
                .sorted(Comparator.comparing(EvaluacionDocentePregunta::getOrden, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(EvaluacionDocentePregunta::getId))
                .map(p -> {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", p.getId());
                    pm.put("texto", p.getTexto());
                    pm.put("orden", p.getOrden());
                    pm.put("bloque", p.getBloque());
                    pm.put("tipo", p.getTipo() != null ? p.getTipo().name() : null);
                    pm.put("opciones", p.getOpciones());
                    return pm;
                })
                .collect(Collectors.toList());
        formMap.put("preguntas", preguntas);
        Map<String, Object> out = new HashMap<>();
        out.put("formulario", formMap);
        out.put("clases", clases);
        out.put("maestros", List.of());
        out.put("pendientesEvaluacionDocentes", pendientes);
        out.put("yaRespondio", pendientes == 0 && !clases.isEmpty());
        out.put("limiteCaracteresInforme", INFORME_PARA_DOCENTE_MAX_CARACTERES);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> contextoParaAutoevaluacion(Maestro maestro) {
        if (maestro == null || maestro.getId() == null) {
            return Map.of("formulario", null, "yaRespondio", false, "maestro", null);
        }
        Optional<EvaluacionDocenteFormulario> opt = obtenerFormularioVigenteParaAutoevaluacion();
        if (opt.isEmpty()) {
            return Map.of("formulario", null, "yaRespondio", false, "maestro", Map.of(
                    "id", maestro.getId(),
                    "nombreCompleto", nombreCompletoMaestro(maestro)
            ));
        }
        EvaluacionDocenteFormulario raw = opt.get();
        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(raw.getId()).orElse(raw);

        Usuario u = maestro.getUsuario();
        List<Map<String, Object>> obligaciones = (u != null && u.getId() != null)
                ? construirObligacionesAutoevaluacion(maestro, f.getId(), u)
                : List.of();
        long pendientes = obligaciones.stream().filter(o -> Boolean.TRUE.equals(o.get("puedeResponder"))).count();
        boolean sinPendientes = obligaciones.stream().noneMatch(o -> Boolean.TRUE.equals(o.get("puedeResponder")));

        Map<String, Object> formMap = new HashMap<>();
        formMap.put("id", f.getId());
        formMap.put("titulo", f.getTitulo());
        formMap.put("descripcion", f.getDescripcion());
        formMap.put("tipo", f.getTipo() != null ? f.getTipo().name() : null);
        List<Map<String, Object>> preguntas = f.getPreguntas().stream()
                .sorted(Comparator.comparing(EvaluacionDocentePregunta::getOrden, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(EvaluacionDocentePregunta::getId))
                .map(p -> {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", p.getId());
                    pm.put("texto", p.getTexto());
                    pm.put("orden", p.getOrden());
                    pm.put("bloque", p.getBloque());
                    pm.put("tipo", p.getTipo() != null ? p.getTipo().name() : null);
                    pm.put("opciones", p.getOpciones());
                    return pm;
                })
                .collect(Collectors.toList());
        formMap.put("preguntas", preguntas);

        Map<String, Object> out = new HashMap<>();
        out.put("formulario", formMap);
        out.put("obligaciones", obligaciones);
        out.put("pendientesEvaluacion", pendientes);
        out.put("sinObligacionesPendientes", sinPendientes);
        out.put("yaRespondio", sinPendientes && !obligaciones.isEmpty());
        out.put("maestro", Map.of(
                "id", maestro.getId(),
                "nombreCompleto", nombreCompletoMaestro(maestro)
        ));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Maestro> listarMaestrosParaEvaluacionAcademica(Usuario evaluadorUsuario) {
        // Secretaría Académica: todos los docentes con grupo o clase asignada.
        // Coordinador Académico: solo docentes con grupo o clase asignada en programas asignados al coordinador.
        boolean esCoord = evaluadorUsuario != null && evaluadorUsuario.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO);
        Set<Long> ids = new LinkedHashSet<>();
        if (esCoord) {
            Set<Long> pids = (evaluadorUsuario.getProgramasAsignados() == null) ? Set.of()
                    : evaluadorUsuario.getProgramasAsignados().stream()
                    .filter(p -> p != null && p.getId() != null)
                    .map(ProgramaEducativo::getId)
                    .collect(Collectors.toSet());
            if (pids.isEmpty()) {
                return List.of();
            }
            ids.addAll(grupoRepository.findDistinctMaestroIdsConGrupoAsignadoEnProgramas(pids));
            ids.addAll(horarioBloqueRepository.findDistinctMaestroIdsConClaseAsignadaEnProgramas(pids));
        } else {
            ids.addAll(grupoRepository.findDistinctMaestroIdsConGrupoAsignado());
            ids.addAll(horarioBloqueRepository.findDistinctMaestroIdsConClaseAsignada());
        }
        if (ids.isEmpty()) return List.of();
        return maestroRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Maestro::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> contextoPruebaParaAdmin(Long formularioId) {
        if (formularioId == null) {
            throw new IllegalArgumentException("formularioId es obligatorio.");
        }
        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        if (!Boolean.TRUE.equals(f.getActivo())) {
            throw new IllegalArgumentException("Este formulario no está activo.");
        }

        List<Map<String, Object>> maestros = maestroRepository.findAll().stream()
                .sorted(Comparator.comparing(Maestro::getId, Comparator.nullsLast(Long::compareTo)))
                .limit(5)
                .map(m -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", m.getId());
                    row.put("nombreCompleto", nombreCompletoMaestro(m));
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> formMap = new HashMap<>();
        formMap.put("id", f.getId());
        formMap.put("titulo", f.getTitulo());
        formMap.put("descripcion", f.getDescripcion());
        formMap.put("tipo", f.getTipo() != null ? f.getTipo().name() : null);
        List<Map<String, Object>> preguntas = f.getPreguntas().stream()
                .sorted(Comparator.comparing(EvaluacionDocentePregunta::getOrden, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(EvaluacionDocentePregunta::getId))
                .map(p -> {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", p.getId());
                    pm.put("texto", p.getTexto());
                    pm.put("orden", p.getOrden());
                    pm.put("bloque", p.getBloque());
                    pm.put("tipo", p.getTipo() != null ? p.getTipo().name() : null);
                    pm.put("opciones", p.getOpciones());
                    return pm;
                })
                .collect(Collectors.toList());
        formMap.put("preguntas", preguntas);

        return Map.of(
                "formulario", formMap,
                "yaRespondio", false,
                "maestros", maestros
        );
    }

    private static String nombreCompletoMaestro(Maestro m) {
        return String.join(" ", m.getNombre(), m.getApellidoPaterno(), m.getApellidoMaterno()).trim();
    }

    private static String etiquetaEvaluadorAcademico(Usuario u) {
        if (u == null) {
            return "—";
        }
        try {
            Personal p = u.getPersonal();
            if (p != null) {
                String s = String.join(" ", p.getNombre(), p.getApellidoPaterno(), p.getApellidoMaterno()).trim();
                if (!s.isBlank()) {
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        return u.getEmail() != null ? u.getEmail() : "—";
    }

    /**
     * Secretaría / Coordinación / Admin: alcance para evaluación académica e informes.
     */
    private void assertUsuarioPuedeGestionarEvaluacionAcademicaMaestro(Usuario u, Long maestroId) {
        if (u == null || maestroId == null) {
            throw new IllegalArgumentException("Datos inválidos.");
        }
        if (u.tieneRol(Usuario.TipoUsuario.ADMIN)) {
            return;
        }
        boolean ok = listarMaestrosParaEvaluacionAcademica(u).stream()
                .anyMatch(m -> m != null && m.getId() != null && m.getId().equals(maestroId));
        if (!ok) {
            throw new IllegalArgumentException("No tienes alcance para gestionar la evaluación académica de este docente.");
        }
    }

    /**
     * La clase (horario) debe pertenecer al alcance de evaluación académica del usuario.
     */
    private void assertUsuarioPuedeGestionarEvaluacionAcademicaHorario(Usuario u, Long horarioBloqueId) {
        if (horarioBloqueId == null) {
            throw new IllegalArgumentException("horarioBloqueId es obligatorio.");
        }
        if (u != null && u.tieneRol(Usuario.TipoUsuario.ADMIN)) {
            return;
        }
        Set<Long> permitidos = listarHorariosEvaluacionAcademicaSecretaria(u).stream()
                .map(HorarioBloque::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!permitidos.contains(horarioBloqueId)) {
            throw new IllegalArgumentException("La clase indicada no está en tu alcance de gestión.");
        }
    }

    private LocalDate finModuloDesdeHorario(HorarioBloque h) {
        LocalDateTime t = instanteAperturaEvaluacionDesdeHorario(h);
        if (t != null) {
            return t.toLocalDate();
        }
        if (h == null) {
            return null;
        }
        if (h.getFechaFin() != null) {
            return h.getFechaFin();
        }
        if (h.getPeriodoAcademico() != null && h.getPeriodoAcademico().getFechaFin() != null) {
            return h.getPeriodoAcademico().getFechaFin();
        }
        return null;
    }

    private static DayOfWeek diaSemanaADayOfWeek(HorarioBloque.DiaSemana dia) {
        if (dia == null) {
            return null;
        }
        return switch (dia) {
            case LUNES -> DayOfWeek.MONDAY;
            case MARTES -> DayOfWeek.TUESDAY;
            case MIERCOLES -> DayOfWeek.WEDNESDAY;
            case JUEVES -> DayOfWeek.THURSDAY;
            case VIERNES -> DayOfWeek.FRIDAY;
            case SABADO -> DayOfWeek.SATURDAY;
            case DOMINGO -> DayOfWeek.SUNDAY;
        };
    }

    /**
     * Momento en que queda disponible la evaluación docente / autoevaluación: fin de la última sesión del bloque
     * en el periodo del horario (última ocurrencia del día de la semana del bloque entre fecha inicio y fin, a {@link HorarioBloque#getHoraFin()}).
     */
    private LocalDateTime instanteAperturaEvaluacionDesdeHorario(HorarioBloque h) {
        if (h == null || h.getDia() == null || h.getHoraFin() == null) {
            return null;
        }
        DayOfWeek target = diaSemanaADayOfWeek(h.getDia());
        if (target == null) {
            return null;
        }
        LocalDate fechaFin = h.getFechaFin();
        if (fechaFin == null && h.getPeriodoAcademico() != null) {
            fechaFin = h.getPeriodoAcademico().getFechaFin();
        }
        LocalDate fechaInicio = h.getFechaInicio();
        if (fechaInicio == null && h.getPeriodoAcademico() != null) {
            fechaInicio = h.getPeriodoAcademico().getFechaInicio();
        }
        if (fechaFin == null) {
            return null;
        }
        if (fechaInicio == null) {
            fechaInicio = fechaFin;
        }
        if (fechaInicio.isAfter(fechaFin)) {
            return null;
        }
        for (LocalDate d = fechaFin; !d.isBefore(fechaInicio); d = d.minusDays(1)) {
            if (d.getDayOfWeek() == target) {
                return LocalDateTime.of(d, h.getHoraFin());
            }
        }
        return null;
    }

    /** La evaluación está disponible sin fecha límite de cierre; solo a partir del instante de fin de la última clase del módulo. */
    private boolean ventanaEvaluacionPorHorario(HorarioBloque h) {
        LocalDateTime t = instanteAperturaEvaluacionDesdeHorario(h);
        if (t == null) {
            return false;
        }
        return !LocalDateTime.now().isBefore(t);
    }

    private static boolean esCalificacionDePeriodoAcademicoActual(Calificacion c, LocalDate hoy) {
        if (c == null || c.getPeriodoAcademico() == null) {
            return false;
        }
        var pa = c.getPeriodoAcademico();
        if (pa.getFechaInicio() == null || pa.getFechaFin() == null) {
            return false;
        }
        return !hoy.isBefore(pa.getFechaInicio()) && !hoy.isAfter(pa.getFechaFin());
    }

    private List<HorarioBloque> horariosCanonicoPorGrupos(Collection<Long> grupoIds) {
        if (grupoIds == null || grupoIds.isEmpty()) {
            return List.of();
        }
        List<HorarioBloque> bloques = horarioBloqueRepository.findByGrupoEntity_IdInAndEstatus(
                grupoIds, HorarioBloque.EstatusHorario.ACTIVO);
        Map<String, HorarioBloque> canon = new LinkedHashMap<>();
        for (HorarioBloque h : bloques) {
            if (h.getGrupoEntity() == null || h.getMaestro() == null || h.getMaestro().getId() == null
                    || h.getAsignatura() == null || h.getAsignatura().getId() == null) {
                continue;
            }
            LocalDate fin = finModuloDesdeHorario(h);
            Long paId = h.getPeriodoAcademico() != null ? h.getPeriodoAcademico().getId() : null;
            String key = h.getGrupoEntity().getId() + "_" + h.getAsignatura().getId() + "_" + h.getMaestro().getId() + "_"
                    + (fin != null ? fin.toString() : "null") + "_" + (paId != null ? paId : "none");
            canon.merge(key, h, (a, b) -> a.getId() < b.getId() ? a : b);
        }
        return new ArrayList<>(canon.values());
    }

    private List<HorarioBloque> horariosCanonicoAlumno(Alumno alumno) {
        if (alumno == null || alumno.getId() == null) {
            return List.of();
        }
        List<Grupo> grupos = grupoRepository.findByAlumnos_Id(alumno.getId());
        Set<Long> gids = grupos.stream().map(Grupo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        return horariosCanonicoPorGrupos(gids);
    }

    private List<HorarioBloque> horariosCanonicoMaestro(Maestro maestro) {
        if (maestro == null || maestro.getId() == null) {
            return List.of();
        }
        List<HorarioBloque> bloques = horarioBloqueRepository.findByMaestro_IdAndEstatus(
                maestro.getId(), HorarioBloque.EstatusHorario.ACTIVO);
        Map<String, HorarioBloque> canon = new LinkedHashMap<>();
        for (HorarioBloque h : bloques) {
            if (h.getGrupoEntity() == null || h.getAsignatura() == null || h.getAsignatura().getId() == null) {
                continue;
            }
            LocalDate fin = finModuloDesdeHorario(h);
            Long paId = h.getPeriodoAcademico() != null ? h.getPeriodoAcademico().getId() : null;
            String key = h.getGrupoEntity().getId() + "_" + h.getAsignatura().getId() + "_"
                    + (fin != null ? fin.toString() : "null") + "_" + (paId != null ? paId : "none");
            canon.merge(key, h, (a, b) -> a.getId() < b.getId() ? a : b);
        }
        return new ArrayList<>(canon.values());
    }

    private Optional<HorarioBloque> resolverHorarioParaCalificacion(Alumno alumno, Calificacion c) {
        if (c == null || c.getGrupo() == null || c.getAsignatura() == null) {
            return Optional.empty();
        }
        List<HorarioBloque> candidatos = horariosCanonicoAlumno(alumno).stream()
                .filter(h -> h.getGrupoEntity() != null && h.getGrupoEntity().getId().equals(c.getGrupo().getId()))
                .filter(h -> h.getAsignatura() != null && h.getAsignatura().getId().equals(c.getAsignatura().getId()))
                .collect(Collectors.toList());
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        if (c.getPeriodoAcademico() != null) {
            Optional<HorarioBloque> porPa = candidatos.stream()
                    .filter(h -> h.getPeriodoAcademico() != null
                            && h.getPeriodoAcademico().getId().equals(c.getPeriodoAcademico().getId()))
                    .min(Comparator.comparing(HorarioBloque::getId));
            if (porPa.isPresent()) {
                return porPa;
            }
        }
        return candidatos.stream().min(Comparator.comparing(HorarioBloque::getId));
    }

    private List<Map<String, Object>> construirObligacionesAlumno(Alumno alumno, Long formularioId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (HorarioBloque h : horariosCanonicoAlumno(alumno)) {
            Maestro m = h.getMaestro();
            LocalDateTime instanteApertura = instanteAperturaEvaluacionDesdeHorario(h);
            LocalDate fin = finModuloDesdeHorario(h);
            boolean ventana = ventanaEvaluacionPorHorario(h);
            String mensajePeriodo = gestionAcademicaEstadoService.validarNuevaEvaluacion(h.getPeriodoAcademico());
            boolean yaEv = respuestaRepository.existsByFormulario_IdAndAlumno_IdAndHorarioBloque_Id(
                    formularioId, alumno.getId(), h.getId());
            Map<String, Object> row = new HashMap<>();
            row.put("horarioBloqueId", h.getId());
            row.put("maestroId", m != null ? m.getId() : null);
            row.put("nombreCompleto", m != null ? nombreCompletoMaestro(m) : "—");
            row.put("asignaturaNombre", h.getAsignatura() != null ? h.getAsignatura().getNombre() : "—");
            row.put("grupoNombre", h.getGrupoEntity() != null ? h.getGrupoEntity().getNombre() : (h.getGrupoNombre() != null ? h.getGrupoNombre() : "—"));
            row.put("fechaFinModulo", fin);
            row.put("instanteAperturaEvaluacion", instanteApertura != null ? instanteApertura.toString() : null);
            row.put("ventanaAbierta", mensajePeriodo == null && ventana);
            row.put("yaEvaluado", yaEv);
            row.put("puedeResponder", mensajePeriodo == null && ventana && !yaEv);
            if (mensajePeriodo != null) {
                row.put("mensaje", mensajePeriodo);
            } else if (!ventana) {
                row.put("mensaje", instanteApertura != null
                        ? "La evaluación se habilita al terminar la última sesión de este módulo en el horario ("
                        + instanteApertura.toString() + ")."
                        : "Define fechas y horario del módulo para calcular cuándo se habilita la evaluación.");
            } else if (yaEv) {
                row.put("mensaje", "Ya enviaste la evaluación para este módulo.");
            } else {
                row.put("mensaje", null);
            }
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> construirObligacionesAutoevaluacion(Maestro maestro, Long formularioId, Usuario evaluadorUsuario) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (HorarioBloque h : horariosCanonicoMaestro(maestro)) {
            LocalDateTime instanteApertura = instanteAperturaEvaluacionDesdeHorario(h);
            LocalDate fin = finModuloDesdeHorario(h);
            boolean ventana = ventanaEvaluacionPorHorario(h);
            String mensajePeriodo = gestionAcademicaEstadoService.validarNuevaEvaluacion(h.getPeriodoAcademico());
            boolean yaEv = respuestaRepository.existsByFormulario_IdAndEvaluadorUsuario_IdAndHorarioBloque_Id(
                    formularioId, evaluadorUsuario.getId(), h.getId());
            Map<String, Object> row = new HashMap<>();
            row.put("horarioBloqueId", h.getId());
            row.put("asignaturaNombre", h.getAsignatura() != null ? h.getAsignatura().getNombre() : "—");
            row.put("grupoNombre", h.getGrupoEntity() != null ? h.getGrupoEntity().getNombre() : (h.getGrupoNombre() != null ? h.getGrupoNombre() : "—"));
            row.put("fechaFinModulo", fin);
            row.put("instanteAperturaEvaluacion", instanteApertura != null ? instanteApertura.toString() : null);
            row.put("ventanaAbierta", mensajePeriodo == null && ventana);
            row.put("yaEvaluado", yaEv);
            row.put("puedeResponder", mensajePeriodo == null && ventana && !yaEv);
            if (mensajePeriodo != null) {
                row.put("mensaje", mensajePeriodo);
            } else if (!ventana) {
                row.put("mensaje", instanteApertura != null
                        ? "La autoevaluación se habilita al terminar la última sesión de este módulo ("
                        + instanteApertura.toString() + ")."
                        : "Define fechas y horario del módulo para calcular cuándo se habilita la autoevaluación.");
            } else if (yaEv) {
                row.put("mensaje", "Ya enviaste la autoevaluación para este módulo.");
            } else {
                row.put("mensaje", null);
            }
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public boolean debeOcultarCalificacionPorEvaluacion(Alumno alumno, Calificacion c) {
        if (alumno == null || c == null) {
            return false;
        }
        // La regla aplica cuando la calificación ya fue enviada por el docente o confirmada.
        // CAPTURADA no es visible para el alumno (se filtra en AlumnoController), así que aquí
        // controlamos EN_REVISION/CONFIRMADA.
        if (c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.EN_REVISION
                && c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.CONFIRMADA) {
            return false;
        }
        Optional<EvaluacionDocenteFormulario> opt = obtenerFormularioVigenteParaAlumno();
        if (opt.isEmpty()) {
            return false;
        }
        Optional<HorarioBloque> ho = resolverHorarioParaCalificacion(alumno, c);
        if (ho.isEmpty()) {
            // Ser conservadores: si no podemos resolver el módulo/hora para validar la evaluación,
            // ocultar la calificación hasta que el horario esté correctamente configurado.
            return true;
        }
        HorarioBloque h = ho.get();
        // No dependemos de la "ventana"; la regla del producto es:
        // el alumno NO ve la calificación enviada/confirmada si no respondió su evaluación docente del módulo.
        return !respuestaRepository.existsByFormulario_IdAndAlumno_IdAndHorarioBloque_Id(
                opt.get().getId(), alumno.getId(), h.getId());
    }

    private void asegurarAlumnoEnGrupoHorario(Alumno alumno, HorarioBloque h) {
        if (h.getGrupoEntity() == null || h.getGrupoEntity().getId() == null) {
            throw new IllegalArgumentException("El horario no está vinculado a un grupo.");
        }
        List<Grupo> grupos = grupoRepository.findByAlumnos_Id(alumno.getId());
        boolean ok = grupos.stream().anyMatch(g -> g.getId().equals(h.getGrupoEntity().getId()));
        if (!ok) {
            throw new IllegalArgumentException("Este módulo de horario no corresponde a tus grupos.");
        }
    }

    @Transactional(readOnly = true)
    public void validarRespuestaPrueba(EvaluacionDocenteResponderRequest req) {
        if (req == null || req.getFormularioId() == null) {
            throw new IllegalArgumentException("formularioId es obligatorio.");
        }
        EvaluacionDocenteFormulario form = formularioRepository.findByIdWithPreguntas(req.getFormularioId())
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        if (!Boolean.TRUE.equals(form.getActivo())) {
            throw new IllegalArgumentException("Este formulario no está activo.");
        }
        List<EvaluacionDocentePregunta> preguntas = form.getPreguntas();
        if (preguntas.isEmpty()) {
            throw new IllegalArgumentException("El formulario no tiene preguntas.");
        }
        Set<Long> preguntaIds = preguntas.stream().map(EvaluacionDocentePregunta::getId).collect(Collectors.toSet());
        if (req.getPorMaestro() == null || req.getPorMaestro().isEmpty()) {
            throw new IllegalArgumentException("Debes evaluar a tus docentes.");
        }
        Set<Long> maestrosEnviados = new HashSet<>();
        for (EvaluacionDocenteResponderRequest.EvaluacionDocenteMaestroRespuesta bloque : req.getPorMaestro()) {
            if (bloque.getMaestroId() == null) {
                throw new IllegalArgumentException("Falta maestroId.");
            }
            if (!maestrosEnviados.add(bloque.getMaestroId())) {
                throw new IllegalArgumentException("Docente duplicado en la respuesta.");
            }
            Maestro maestro = maestroRepository.findById(bloque.getMaestroId())
                    .orElseThrow(() -> new IllegalArgumentException("Docente no encontrado."));
            if (bloque.getValores() == null) {
                throw new IllegalArgumentException("Faltan calificaciones para un docente.");
            }

            Map<Long, Integer> porPregunta = new HashMap<>();
            Map<Long, String> porPreguntaTexto = new HashMap<>();
            for (EvaluacionDocenteResponderRequest.EvaluacionDocenteParPreguntaValor pv : bloque.getValores()) {
                if (pv.getPreguntaId() == null) {
                    throw new IllegalArgumentException("Falta preguntaId.");
                }
                if (!preguntaIds.contains(pv.getPreguntaId())) {
                    throw new IllegalArgumentException("Pregunta no pertenece al formulario.");
                }
                EvaluacionDocentePregunta preg = preguntas.stream()
                        .filter(p -> p.getId().equals(pv.getPreguntaId()))
                        .findFirst()
                        .orElseThrow();
                if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                    if (pv.getValor() == null) {
                        throw new IllegalArgumentException("Cada pregunta debe tener valor (1–5).");
                    }
                    if (pv.getValor() < 1 || pv.getValor() > 5) {
                        throw new IllegalArgumentException("Las calificaciones deben ser entre 1 y 5.");
                    }
                    porPregunta.put(pv.getPreguntaId(), pv.getValor());
                } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.ABIERTA) {
                    String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                    if (txt.isBlank()) {
                        throw new IllegalArgumentException("Las preguntas abiertas requieren respuesta.");
                    }
                    porPreguntaTexto.put(pv.getPreguntaId(), txt);
                } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.OPCION_MULTIPLE) {
                    String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                    if (txt.isBlank()) {
                        throw new IllegalArgumentException("Las preguntas de opción múltiple requieren selección.");
                    }
                    porPreguntaTexto.put(pv.getPreguntaId(), txt);
                }
            }
            if ((porPregunta.size() + porPreguntaTexto.size()) != preguntaIds.size()) {
                throw new IllegalArgumentException("Debes responder todas las preguntas para cada docente.");
            }
            // Nota: en modo prueba no persistimos; solo validamos que el maestro exista.
            if (maestro.getId() == null) {
                throw new IllegalArgumentException("Docente inválido.");
            }
        }
    }

    @Transactional
    public void guardarRespuestaAlumno(Alumno alumno, EvaluacionDocenteResponderRequest req) {
        if (req == null || req.getFormularioId() == null) {
            throw new IllegalArgumentException("formularioId es obligatorio.");
        }
        if (req.getHorarioBloqueId() == null) {
            throw new IllegalArgumentException("horarioBloqueId es obligatorio (evaluación por módulo/asignatura).");
        }
        EvaluacionDocenteFormulario form = formularioRepository.findByIdWithPreguntas(req.getFormularioId())
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        if (form.getTipo() != EvaluacionDocenteFormulario.TipoEvaluacion.POR_ALUMNOS) {
            throw new IllegalArgumentException("El formulario no corresponde a evaluación por alumnos.");
        }
        if (!Boolean.TRUE.equals(form.getActivo())) {
            throw new IllegalArgumentException("Este formulario no está activo.");
        }
        HorarioBloque h = horarioBloqueRepository.findById(req.getHorarioBloqueId())
                .orElseThrow(() -> new IllegalArgumentException("Bloque de horario no encontrado."));
        if (h.getEstatus() != HorarioBloque.EstatusHorario.ACTIVO) {
            throw new IllegalArgumentException("El bloque de horario no está activo.");
        }
        String mensajePeriodo = gestionAcademicaEstadoService.validarNuevaEvaluacion(h.getPeriodoAcademico());
        if (mensajePeriodo != null) {
            throw new IllegalArgumentException(mensajePeriodo);
        }
        asegurarAlumnoEnGrupoHorario(alumno, h);
        if (respuestaRepository.existsByFormulario_IdAndAlumno_IdAndHorarioBloque_Id(form.getId(), alumno.getId(), h.getId())) {
            throw new IllegalArgumentException("Ya enviaste la evaluación para este módulo.");
        }
        if (!ventanaEvaluacionPorHorario(h)) {
            LocalDateTime t = instanteAperturaEvaluacionDesdeHorario(h);
            throw new IllegalArgumentException(t != null
                    ? "La evaluación de este módulo se habilita al terminar la última sesión en el horario (" + t + ")."
                    : "No se pudo determinar la fecha de fin del módulo en el horario.");
        }
        if (h.getMaestro() == null || h.getMaestro().getId() == null) {
            throw new IllegalArgumentException("El horario no tiene docente asignado.");
        }
        List<EvaluacionDocentePregunta> preguntas = form.getPreguntas();
        if (preguntas.isEmpty()) {
            throw new IllegalArgumentException("El formulario no tiene preguntas.");
        }
        Set<Long> preguntaIds = preguntas.stream().map(EvaluacionDocentePregunta::getId).collect(Collectors.toSet());
        if (req.getPorMaestro() == null || req.getPorMaestro().size() != 1) {
            throw new IllegalArgumentException("Envía la evaluación de un solo docente por módulo.");
        }
        EvaluacionDocenteResponderRequest.EvaluacionDocenteMaestroRespuesta bloque = req.getPorMaestro().get(0);
        if (bloque == null || bloque.getMaestroId() == null) {
            throw new IllegalArgumentException("Falta maestroId.");
        }
        if (!bloque.getMaestroId().equals(h.getMaestro().getId())) {
            throw new IllegalArgumentException("Debes evaluar al docente que imparte este módulo en tu horario.");
        }
        Maestro maestro = maestroRepository.findById(bloque.getMaestroId())
                .orElseThrow(() -> new IllegalArgumentException("Docente no encontrado."));
        if (bloque.getValores() == null) {
            throw new IllegalArgumentException("Faltan respuestas.");
        }
        Map<Long, Integer> porPregunta = new HashMap<>();
        Map<Long, String> porPreguntaTexto = new HashMap<>();
        for (EvaluacionDocenteResponderRequest.EvaluacionDocenteParPreguntaValor pv : bloque.getValores()) {
            if (pv == null || pv.getPreguntaId() == null) {
                throw new IllegalArgumentException("Falta preguntaId.");
            }
            if (!preguntaIds.contains(pv.getPreguntaId())) {
                throw new IllegalArgumentException("Pregunta no pertenece al formulario.");
            }
            EvaluacionDocentePregunta preg = preguntas.stream().filter(p -> p.getId().equals(pv.getPreguntaId())).findFirst().orElseThrow();
            if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                if (pv.getValor() == null) {
                    throw new IllegalArgumentException("Cada pregunta debe tener valor (1–5).");
                }
                if (pv.getValor() < 1 || pv.getValor() > 5) {
                    throw new IllegalArgumentException("Las calificaciones deben ser entre 1 y 5.");
                }
                porPregunta.put(pv.getPreguntaId(), pv.getValor());
            } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.ABIERTA) {
                String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                if (txt.isBlank()) {
                    throw new IllegalArgumentException("Las preguntas abiertas requieren respuesta.");
                }
                porPreguntaTexto.put(pv.getPreguntaId(), txt);
            } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.OPCION_MULTIPLE) {
                String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                if (txt.isBlank()) {
                    throw new IllegalArgumentException("Las preguntas de opción múltiple requieren selección.");
                }
                porPreguntaTexto.put(pv.getPreguntaId(), txt);
            }
        }
        if ((porPregunta.size() + porPreguntaTexto.size()) != preguntaIds.size()) {
            throw new IllegalArgumentException("Debes responder todas las preguntas.");
        }

        EvaluacionDocenteRespuesta resp = new EvaluacionDocenteRespuesta();
        resp.setFormulario(form);
        resp.setAlumno(alumno);
        resp.setEvaluadorUsuario(null);
        resp.setHorarioBloque(h);
        resp.setFechaRespuesta(LocalDateTime.now());
        for (Long pid : preguntaIds) {
            EvaluacionDocentePregunta pregunta = preguntas.stream()
                    .filter(p -> p.getId().equals(pid))
                    .findFirst()
                    .orElseThrow();
            EvaluacionDocenteItem item = new EvaluacionDocenteItem();
            item.setRespuesta(resp);
            item.setMaestro(maestro);
            item.setPregunta(pregunta);
            if (pregunta.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                item.setValor(porPregunta.get(pid));
                item.setValorTexto(null);
            } else {
                item.setValor(null);
                item.setValorTexto(porPreguntaTexto.get(pid));
            }
            resp.getItems().add(item);
        }
        respuestaRepository.save(resp);
    }

    @Transactional
    public void guardarRespuestaSecretaria(Usuario evaluadorUsuario, EvaluacionDocenteResponderRequest req) {
        if (evaluadorUsuario == null || evaluadorUsuario.getId() == null) {
            throw new IllegalArgumentException("Usuario evaluador inválido.");
        }
        if (req == null || req.getFormularioId() == null) {
            throw new IllegalArgumentException("formularioId es obligatorio.");
        }
        EvaluacionDocenteFormulario form = formularioRepository.findByIdWithPreguntas(req.getFormularioId())
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        if (form.getTipo() != EvaluacionDocenteFormulario.TipoEvaluacion.POR_SECRETARIA_ACADEMICA) {
            throw new IllegalArgumentException("El formulario no corresponde a Evaluación Académica.");
        }
        if (!esFormularioVigente(form, LocalDate.now())) {
            throw new IllegalArgumentException("Este formulario no está vigente.");
        }
        List<EvaluacionDocentePregunta> preguntas = form.getPreguntas();
        if (preguntas == null || preguntas.isEmpty()) {
            throw new IllegalArgumentException("El formulario no tiene preguntas.");
        }
        Set<Long> preguntaIds = preguntas.stream().map(EvaluacionDocentePregunta::getId).collect(Collectors.toSet());
        Set<String> bloquesFormulario = preguntas.stream()
                .map(p -> p != null ? p.getBloque() : null)
                .filter(b -> b != null && !b.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<HorarioBloque> horariosPermitidosLista = listarHorariosEvaluacionAcademicaSecretaria(evaluadorUsuario);
        Set<Long> horariosPermitidos = horariosPermitidosLista.stream()
                .map(HorarioBloque::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (horariosPermitidos.isEmpty()) {
            throw new IllegalArgumentException("No hay clases (horarios) en tu alcance para evaluar.");
        }

        if (req.getPorMaestro() == null || req.getPorMaestro().isEmpty()) {
            throw new IllegalArgumentException("Incluye al menos una clase evaluada.");
        }

        Set<String> paresEnviados = new HashSet<>();
        for (EvaluacionDocenteResponderRequest.EvaluacionDocenteMaestroRespuesta bloque : req.getPorMaestro()) {
            if (bloque == null || bloque.getMaestroId() == null) {
                throw new IllegalArgumentException("Falta maestroId.");
            }
            if (bloque.getHorarioBloqueId() == null) {
                throw new IllegalArgumentException("horarioBloqueId es obligatorio (una evaluación por clase).");
            }
            String parKey = bloque.getMaestroId() + ":" + bloque.getHorarioBloqueId();
            if (!paresEnviados.add(parKey)) {
                throw new IllegalArgumentException("Clase duplicada en la solicitud.");
            }
            if (!horariosPermitidos.contains(bloque.getHorarioBloqueId())) {
                throw new IllegalArgumentException("La clase no corresponde a tu alcance de gestión.");
            }
            HorarioBloque h = horarioBloqueRepository.findById(bloque.getHorarioBloqueId())
                    .orElseThrow(() -> new IllegalArgumentException("Bloque de horario no encontrado."));
            if (h.getEstatus() != HorarioBloque.EstatusHorario.ACTIVO) {
                throw new IllegalArgumentException("El bloque de horario no está activo.");
            }
            String mensajePeriodo = gestionAcademicaEstadoService.validarNuevaEvaluacion(h.getPeriodoAcademico());
            if (mensajePeriodo != null) {
                throw new IllegalArgumentException(mensajePeriodo);
            }
            if (h.getMaestro() == null || !h.getMaestro().getId().equals(bloque.getMaestroId())) {
                throw new IllegalArgumentException("El docente no coincide con la clase indicada.");
            }
            List<EvaluacionDocenteRespuesta> yaEstaClase = respuestaRepository.findEvaluacionesAcademicasPorFormularioMaestroYHorario(
                    form.getId(), bloque.getMaestroId(), bloque.getHorarioBloqueId());
            if (!yaEstaClase.isEmpty()) {
                throw new IllegalArgumentException(
                        "Esta clase ya tiene una evaluación académica registrada para este formulario.");
            }
            Maestro maestro = maestroRepository.findById(bloque.getMaestroId())
                    .orElseThrow(() -> new IllegalArgumentException("Docente no encontrado."));
            if (bloque.getValores() == null) {
                throw new IllegalArgumentException("Faltan respuestas para un docente.");
            }
            Map<Long, Integer> porPregunta = new HashMap<>();
            Map<Long, String> porPreguntaTexto = new HashMap<>();

            for (EvaluacionDocenteResponderRequest.EvaluacionDocenteParPreguntaValor pv : bloque.getValores()) {
                if (pv == null || pv.getPreguntaId() == null) {
                    throw new IllegalArgumentException("Falta preguntaId.");
                }
                if (!preguntaIds.contains(pv.getPreguntaId())) {
                    throw new IllegalArgumentException("Pregunta no pertenece al formulario.");
                }
                EvaluacionDocentePregunta preg = preguntas.stream()
                        .filter(p -> p.getId().equals(pv.getPreguntaId()))
                        .findFirst()
                        .orElseThrow();
                if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                    if (pv.getValor() == null) {
                        throw new IllegalArgumentException("Cada pregunta debe tener valor (1–5).");
                    }
                    if (pv.getValor() < 1 || pv.getValor() > 5) {
                        throw new IllegalArgumentException("Las calificaciones deben ser entre 1 y 5.");
                    }
                    porPregunta.put(pv.getPreguntaId(), pv.getValor());
                } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.ABIERTA) {
                    String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                    if (txt.isBlank()) {
                        throw new IllegalArgumentException("Las preguntas abiertas requieren respuesta.");
                    }
                    porPreguntaTexto.put(pv.getPreguntaId(), txt);
                } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.OPCION_MULTIPLE) {
                    String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                    if (txt.isBlank()) {
                        throw new IllegalArgumentException("Las preguntas de opción múltiple requieren selección.");
                    }
                    porPreguntaTexto.put(pv.getPreguntaId(), txt);
                }
            }

            if ((porPregunta.size() + porPreguntaTexto.size()) != preguntaIds.size()) {
                throw new IllegalArgumentException("Debes responder todas las preguntas para cada docente incluido.");
            }

            EvaluacionDocenteRespuesta resp = new EvaluacionDocenteRespuesta();
            resp.setFormulario(form);
            resp.setAlumno(null);
            resp.setEvaluadorUsuario(evaluadorUsuario);
            resp.setMaestroEvaluado(maestro);
            resp.setHorarioBloque(h);
            // Momento en que se registra el envío (visita); no viene del cliente.
            resp.setFechaVisita(LocalDateTime.now());
            resp.setFechaRespuesta(LocalDateTime.now());

            // Observaciones por bloque (opcionales)
            if (bloque.getObservacionesBloque() != null && !bloque.getObservacionesBloque().isEmpty()) {
                for (EvaluacionDocenteResponderRequest.ObservacionBloque ob : bloque.getObservacionesBloque()) {
                    if (ob == null) continue;
                    String bname = ob.getBloque() != null ? ob.getBloque().trim() : "";
                    String txt = ob.getTexto() != null ? ob.getTexto().trim() : "";
                    if (bname.isBlank() || txt.isBlank()) continue;
                    if (!bloquesFormulario.isEmpty() && !bloquesFormulario.contains(bname)) continue;
                    resp.getObservacionesBloque().add(new EvaluacionDocenteObservacionBloque(bname, txt));
                }
            }

            for (Long pid : preguntaIds) {
                EvaluacionDocentePregunta pregunta = preguntas.stream()
                        .filter(p -> p.getId().equals(pid))
                        .findFirst()
                        .orElseThrow();
                EvaluacionDocenteItem item = new EvaluacionDocenteItem();
                item.setRespuesta(resp);
                item.setMaestro(maestro);
                item.setPregunta(pregunta);
                if (pregunta.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                    item.setValor(porPregunta.get(pid));
                    item.setValorTexto(null);
                } else {
                    item.setValor(null);
                    item.setValorTexto(porPreguntaTexto.get(pid));
                }
                resp.getItems().add(item);
            }
            respuestaRepository.save(resp);
        }
    }

    @Transactional
    public void guardarRespuestaAutoevaluacion(Maestro maestro, EvaluacionDocenteResponderRequest req) {
        if (maestro == null || maestro.getId() == null) {
            throw new IllegalArgumentException("Docente inválido.");
        }
        Usuario evaluadorUsuario = maestro.getUsuario();
        if (evaluadorUsuario == null || evaluadorUsuario.getId() == null) {
            throw new IllegalArgumentException("Usuario del docente inválido.");
        }
        if (req == null || req.getFormularioId() == null) {
            throw new IllegalArgumentException("formularioId es obligatorio.");
        }
        if (req.getHorarioBloqueId() == null) {
            throw new IllegalArgumentException("horarioBloqueId es obligatorio (autoevaluación por módulo).");
        }
        EvaluacionDocenteFormulario form = formularioRepository.findByIdWithPreguntas(req.getFormularioId())
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        if (form.getTipo() != EvaluacionDocenteFormulario.TipoEvaluacion.AUTOEVALUACION) {
            throw new IllegalArgumentException("El formulario no corresponde a Autoevaluación.");
        }
        if (!Boolean.TRUE.equals(form.getActivo())) {
            throw new IllegalArgumentException("Este formulario no está activo.");
        }
        HorarioBloque h = horarioBloqueRepository.findById(req.getHorarioBloqueId())
                .orElseThrow(() -> new IllegalArgumentException("Bloque de horario no encontrado."));
        if (h.getEstatus() != HorarioBloque.EstatusHorario.ACTIVO) {
            throw new IllegalArgumentException("El bloque de horario no está activo.");
        }
        String mensajePeriodo = gestionAcademicaEstadoService.validarNuevaEvaluacion(h.getPeriodoAcademico());
        if (mensajePeriodo != null) {
            throw new IllegalArgumentException(mensajePeriodo);
        }
        if (h.getMaestro() == null || !h.getMaestro().getId().equals(maestro.getId())) {
            throw new IllegalArgumentException("La autoevaluación corresponde al bloque donde impartes clase.");
        }
        if (!ventanaEvaluacionPorHorario(h)) {
            LocalDateTime t = instanteAperturaEvaluacionDesdeHorario(h);
            throw new IllegalArgumentException(t != null
                    ? "La autoevaluación se habilita al terminar la última sesión de este módulo (" + t + ")."
                    : "No se pudo determinar la fecha de fin del módulo en el horario.");
        }
        if (respuestaRepository.existsByFormulario_IdAndEvaluadorUsuario_IdAndHorarioBloque_Id(
                form.getId(), evaluadorUsuario.getId(), h.getId())) {
            throw new IllegalArgumentException("Ya enviaste la autoevaluación para este módulo.");
        }
        List<EvaluacionDocentePregunta> preguntas = form.getPreguntas();
        if (preguntas == null || preguntas.isEmpty()) {
            throw new IllegalArgumentException("El formulario no tiene preguntas.");
        }
        Set<Long> preguntaIds = preguntas.stream().map(EvaluacionDocentePregunta::getId).collect(Collectors.toSet());

        if (req.getPorMaestro() == null || req.getPorMaestro().isEmpty()) {
            throw new IllegalArgumentException("Faltan respuestas.");
        }
        if (req.getPorMaestro().size() != 1) {
            throw new IllegalArgumentException("La autoevaluación debe enviarse solo para el propio docente.");
        }
        EvaluacionDocenteResponderRequest.EvaluacionDocenteMaestroRespuesta bloque = req.getPorMaestro().get(0);
        if (bloque == null || bloque.getMaestroId() == null) {
            throw new IllegalArgumentException("Falta maestroId.");
        }
        if (!String.valueOf(bloque.getMaestroId()).equals(String.valueOf(maestro.getId()))) {
            throw new IllegalArgumentException("La autoevaluación solo puede responderse para el propio docente.");
        }
        if (bloque.getValores() == null) {
            throw new IllegalArgumentException("Faltan respuestas.");
        }

        Map<Long, Integer> porPregunta = new HashMap<>();
        Map<Long, String> porPreguntaTexto = new HashMap<>();
        for (EvaluacionDocenteResponderRequest.EvaluacionDocenteParPreguntaValor pv : bloque.getValores()) {
            if (pv == null || pv.getPreguntaId() == null) {
                throw new IllegalArgumentException("Falta preguntaId.");
            }
            if (!preguntaIds.contains(pv.getPreguntaId())) {
                throw new IllegalArgumentException("Pregunta no pertenece al formulario.");
            }
            EvaluacionDocentePregunta preg = preguntas.stream()
                    .filter(p -> p.getId().equals(pv.getPreguntaId()))
                    .findFirst()
                    .orElseThrow();
            if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                if (pv.getValor() == null) throw new IllegalArgumentException("Cada pregunta debe tener valor (1–5).");
                if (pv.getValor() < 1 || pv.getValor() > 5) throw new IllegalArgumentException("Las calificaciones deben ser entre 1 y 5.");
                porPregunta.put(pv.getPreguntaId(), pv.getValor());
            } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.ABIERTA) {
                String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                if (txt.isBlank()) throw new IllegalArgumentException("Las preguntas abiertas requieren respuesta.");
                porPreguntaTexto.put(pv.getPreguntaId(), txt);
            } else if (preg.getTipo() == EvaluacionDocentePregunta.TipoPregunta.OPCION_MULTIPLE) {
                String txt = pv.getValorTexto() != null ? pv.getValorTexto().trim() : "";
                if (txt.isBlank()) throw new IllegalArgumentException("Las preguntas de opción múltiple requieren selección.");
                porPreguntaTexto.put(pv.getPreguntaId(), txt);
            }
        }
        if ((porPregunta.size() + porPreguntaTexto.size()) != preguntaIds.size()) {
            throw new IllegalArgumentException("Debes responder todas las preguntas.");
        }

        EvaluacionDocenteRespuesta resp = new EvaluacionDocenteRespuesta();
        resp.setFormulario(form);
        resp.setAlumno(null);
        resp.setEvaluadorUsuario(evaluadorUsuario);
        resp.setHorarioBloque(h);
        resp.setFechaRespuesta(LocalDateTime.now());

        for (Long pid : preguntaIds) {
            EvaluacionDocentePregunta pregunta = preguntas.stream()
                    .filter(p -> p.getId().equals(pid))
                    .findFirst()
                    .orElseThrow();
            EvaluacionDocenteItem item = new EvaluacionDocenteItem();
            item.setRespuesta(resp);
            item.setMaestro(maestro);
            item.setPregunta(pregunta);
            if (pregunta.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                item.setValor(porPregunta.get(pid));
                item.setValorTexto(null);
            } else {
                item.setValor(null);
                item.setValorTexto(porPreguntaTexto.get(pid));
            }
            resp.getItems().add(item);
        }
        respuestaRepository.save(resp);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resultadosAnonimosParaMaestro(Maestro maestro, Long formularioId, Long horarioBloqueId) {
        EvaluacionDocenteFormulario form;
        if (formularioId != null) {
            form = formularioRepository.findByIdWithPreguntas(formularioId)
                    .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        } else {
            form = obtenerFormularioVigenteParaAlumno().orElse(null);
            if (form == null) {
                return Map.of("formulario", null, "preguntas", List.of(), "mensaje", "No hay formulario de evaluación docente (por alumnos) activo.");
            }
            form = formularioRepository.findByIdWithPreguntas(form.getId()).orElse(form);
        }
        if (form.getTipo() != EvaluacionDocenteFormulario.TipoEvaluacion.POR_ALUMNOS) {
            return Map.of("formulario", null, "mensaje", "Los resultados de estudiantes corresponden al formulario tipo evaluación por alumnos.");
        }

        Map<String, Object> formularioMap = new HashMap<>();
        formularioMap.put("id", form.getId());
        formularioMap.put("titulo", form.getTitulo());

        if (horarioBloqueId != null) {
            HorarioBloque hb = horarioBloqueRepository.findById(horarioBloqueId).orElse(null);
            if (hb == null || hb.getMaestro() == null || !hb.getMaestro().getId().equals(maestro.getId())) {
                return Map.of("formulario", formularioMap, "puedeVerResultados", false,
                        "mensaje", "El horario indicado no corresponde a tus clases.");
            }
            if (hb.getGrupoEntity() == null || hb.getAsignatura() == null) {
                return Map.of("formulario", formularioMap, "puedeVerResultados", false,
                        "mensaje", "El bloque de horario no tiene grupo o asignatura.");
            }
            Maestro maestroDb = maestroRepository.findById(maestro.getId()).orElse(maestro);
            Optional<EvaluacionDocenteFormulario> formAuto = obtenerFormularioVigenteParaAutoevaluacion();
            if (formAuto.isPresent()) {
                Usuario evalUser = maestroDb.getUsuario();
                if (evalUser != null && evalUser.getId() != null
                        && !respuestaRepository.existsByFormulario_IdAndEvaluadorUsuario_IdAndHorarioBloque_Id(
                        formAuto.get().getId(), evalUser.getId(), horarioBloqueId)) {
                    Map<String, Object> bloqueo = new HashMap<>();
                    bloqueo.put("formulario", formularioMap);
                    bloqueo.put("horarioBloqueId", horarioBloqueId);
                    bloqueo.put("puedeVerResultados", false);
                    bloqueo.put("mensaje", "Primero debes completar y enviar tu autoevaluación para este módulo. Después podrás consultar los resultados anónimos de tus estudiantes, cuando hayas capturado y enviado a revisión las calificaciones de todos ellos.");
                    bloqueo.put("estadisticas", List.of());
                    bloqueo.put("respuestasTexto", List.of());
                    bloqueo.put("totalRespuestasAlumno", 0);
                    bloqueo.put("docente", Map.of("id", maestro.getId(), "mensaje", "Resultados anónimos por reactivo (promedio)."));
                    return bloqueo;
                }
            }
            Long grupoId = hb.getGrupoEntity().getId();
            Long asignaturaId = hb.getAsignatura().getId();
            long totalInscritos = grupoRepository.countAlumnosByGrupoId(grupoId);
            if (totalInscritos > 0) {
                long conCaptura = calificacionRepository.countDistinctAlumnosConCalificacionEnModulo(grupoId, asignaturaId);
                long enviados = calificacionRepository.countDistinctAlumnosModuloEnRevisionOConfirmada(grupoId, asignaturaId);
                if (conCaptura < totalInscritos) {
                    Map<String, Object> bloqueo = new HashMap<>();
                    bloqueo.put("formulario", formularioMap);
                    bloqueo.put("horarioBloqueId", horarioBloqueId);
                    bloqueo.put("puedeVerResultados", false);
                    bloqueo.put("mensaje", "Debes capturar la calificación de todos tus estudiantes inscritos en este módulo (grupo y asignatura). "
                            + "Aún faltan alumnos por calificar antes de consultar los resultados anónimos.");
                    bloqueo.put("estadisticas", List.of());
                    bloqueo.put("respuestasTexto", List.of());
                    bloqueo.put("totalRespuestasAlumno", 0);
                    bloqueo.put("totalAlumnosGrupo", totalInscritos);
                    bloqueo.put("alumnosConCalificacionCapturada", conCaptura);
                    bloqueo.put("docente", Map.of("id", maestro.getId(), "mensaje", "Resultados anónimos por reactivo (promedio)."));
                    return bloqueo;
                }
                if (enviados < totalInscritos) {
                    Map<String, Object> bloqueo = new HashMap<>();
                    bloqueo.put("formulario", formularioMap);
                    bloqueo.put("horarioBloqueId", horarioBloqueId);
                    bloqueo.put("puedeVerResultados", false);
                    bloqueo.put("mensaje", "Ya capturaste las notas de todos los alumnos; falta enviar a revisión la calificación de cada uno en este módulo "
                            + "(desde Calificaciones: enviar a revisión). Hasta que conste el envío de todos, no se muestran los resultados anónimos.");
                    bloqueo.put("estadisticas", List.of());
                    bloqueo.put("respuestasTexto", List.of());
                    bloqueo.put("totalRespuestasAlumno", 0);
                    bloqueo.put("totalAlumnosGrupo", totalInscritos);
                    bloqueo.put("alumnosCalificacionEnviada", enviados);
                    bloqueo.put("docente", Map.of("id", maestro.getId(), "mensaje", "Resultados anónimos por reactivo (promedio)."));
                    return bloqueo;
                }
            }
        } else {
            if (!calificacionRepository.existsByMaestroConCalificacionesEnviadasOConfirmadas(maestro.getId())) {
                Map<String, Object> bloqueo = new HashMap<>();
                bloqueo.put("formulario", formularioMap);
                bloqueo.put("puedeVerResultados", false);
                bloqueo.put("mensaje", "Captura y envía a revisión las calificaciones de todos tus estudiantes en al menos un módulo para consultar los resultados agregados de la evaluación docente.");
                bloqueo.put("estadisticas", List.of());
                bloqueo.put("respuestasTexto", List.of());
                bloqueo.put("totalRespuestasAlumno", 0);
                bloqueo.put("docente", Map.of("id", maestro.getId(), "mensaje", "Resultados anónimos por reactivo (promedio)."));
                return bloqueo;
            }
        }

        Map<Long, EvaluacionDocentePregunta.TipoPregunta> tipoPorPregunta = form.getPreguntas().stream()
                .collect(Collectors.toMap(EvaluacionDocentePregunta::getId, EvaluacionDocentePregunta::getTipo, (a, b) -> a));
        Map<Long, String> textoPregunta = form.getPreguntas().stream()
                .collect(Collectors.toMap(EvaluacionDocentePregunta::getId, EvaluacionDocentePregunta::getTexto, (a, b) -> a));

        List<Object[]> rows = horarioBloqueId == null
                ? itemRepository.estadisticasAnonimasPorMaestroYFormulario(maestro.getId(), form.getId())
                : itemRepository.estadisticasAnonimasPorMaestroFormularioYBloque(maestro.getId(), form.getId(), horarioBloqueId);
        Map<Long, Map<String, Object>> statsPorPreguntaLikert = new LinkedHashMap<>();
        Long totalEval = null;
        for (Object[] row : rows) {
            Long pid = (Long) row[0];
            if (tipoPorPregunta.get(pid) != EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                continue;
            }
            Double avg = row[1] != null ? ((Number) row[1]).doubleValue() : null;
            long cnt = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            if (totalEval == null) {
                totalEval = cnt;
            }
            Map<String, Object> e = new HashMap<>();
            e.put("preguntaId", pid);
            e.put("preguntaTexto", textoPregunta.getOrDefault(pid, "—"));
            e.put("promedio", avg != null ? Math.round(avg * 100.0) / 100.0 : null);
            e.put("numeroEvaluaciones", cnt);
            statsPorPreguntaLikert.put(pid, e);
        }

        // Por módulo: exigir una respuesta por alumno con calificación en ese grupo/asignatura (alineado a la evaluación obligatoria para ver calificación).
        if (horarioBloqueId != null) {
            long esperados = 0;
            try {
                HorarioBloque hb = horarioBloqueRepository.findById(horarioBloqueId).orElse(null);
                if (hb != null && hb.getGrupoEntity() != null && hb.getGrupoEntity().getId() != null && hb.getAsignatura() != null && hb.getAsignatura().getId() != null) {
                    esperados = calificacionRepository.countDistinctAlumnosConCalificacionEnModulo(
                            hb.getGrupoEntity().getId(), hb.getAsignatura().getId());
                    if (esperados <= 0) {
                        esperados = grupoRepository.countAlumnosByGrupoId(hb.getGrupoEntity().getId());
                    }
                }
            } catch (Exception ignored) {
            }
            long recibidos = totalEval != null ? totalEval : 0L;
            if (esperados > 0 && recibidos < esperados) {
                Map<String, Object> bloqueo = new HashMap<>();
                bloqueo.put("formulario", formularioMap);
                bloqueo.put("horarioBloqueId", horarioBloqueId);
                bloqueo.put("puedeVerResultados", false);
                bloqueo.put("mensaje", "Aún faltan respuestas de estudiantes con calificación en este módulo. Respuestas recibidas: "
                        + recibidos + " de " + esperados + ".");
                bloqueo.put("estadisticas", List.of());
                bloqueo.put("respuestasTexto", List.of());
                bloqueo.put("totalRespuestasAlumno", recibidos);
                bloqueo.put("totalAlumnosEsperados", esperados);
                bloqueo.put("docente", Map.of("id", maestro.getId(), "mensaje", "Los resultados se muestran cuando todos los alumnos con calificación en este módulo hayan respondido."));
                return bloqueo;
            }
        }

        Usuario usuarioDocente = null;
        try {
            usuarioDocente = maestroRepository.findById(maestro.getId()).map(Maestro::getUsuario).orElse(null);
        } catch (Exception ignored) {
        }

        List<Object[]> autoevalLikertOrdenados = new ArrayList<>();
        if (horarioBloqueId != null && usuarioDocente != null && usuarioDocente.getId() != null) {
            autoevalLikertOrdenados = new ArrayList<>(itemRepository.findAutoevalLikertValoresPorUsuarioYHorario(
                    usuarioDocente.getId(), horarioBloqueId));
            autoevalLikertOrdenados.sort(Comparator
                    .comparing((Object[] a) -> a[1] != null ? ((Number) a[1]).intValue() : Integer.MAX_VALUE)
                    .thenComparing(a -> (Long) a[0]));
        }

        List<EvaluacionDocentePregunta> likertEnOrdenFormulario = form.getPreguntas().stream()
                .filter(p -> p.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5)
                .sorted(Comparator.comparing(EvaluacionDocentePregunta::getOrden, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(EvaluacionDocentePregunta::getId))
                .collect(Collectors.toList());

        List<Map<String, Object>> estadisticas = new ArrayList<>();
        for (int idx = 0; idx < likertEnOrdenFormulario.size(); idx++) {
            EvaluacionDocentePregunta p = likertEnOrdenFormulario.get(idx);
            Map<String, Object> e = statsPorPreguntaLikert.get(p.getId());
            if (e == null) {
                continue;
            }
            if (horarioBloqueId != null && idx < autoevalLikertOrdenados.size()) {
                Object[] ar = autoevalLikertOrdenados.get(idx);
                Integer vAuto = ar[2] != null ? ((Number) ar[2]).intValue() : null;
                Object promObj = e.get("promedio");
                if (vAuto != null && promObj instanceof Number) {
                    double prom = ((Number) promObj).doubleValue();
                    double delta = Math.round((vAuto - prom) * 100.0) / 100.0;
                    e.put("valorAutoevaluacion", vAuto);
                    e.put("diferenciaVsAlumnos", delta);
                    double ad = Math.abs(delta);
                    if (ad <= 0.5) {
                        e.put("semaforo", "VERDE");
                        e.put("interpretacionBrecha", "Coincidencia");
                    } else if (ad < 0.9) {
                        e.put("semaforo", "AMARILLO");
                        e.put("interpretacionBrecha", "Brecha moderada");
                    } else {
                        e.put("semaforo", "ROJO");
                        e.put("interpretacionBrecha", "Brecha significativa");
                    }
                }
            }
            estadisticas.add(e);
        }

        List<Object[]> txtRows = horarioBloqueId == null
                ? itemRepository.respuestasTextoAnonimasPorMaestroYFormulario(maestro.getId(), form.getId())
                : itemRepository.respuestasTextoAnonimasPorMaestroFormularioYBloque(maestro.getId(), form.getId(), horarioBloqueId);
        Map<Long, List<String>> txtByPid = new LinkedHashMap<>();
        for (Object[] row : txtRows) {
            Long pid = (Long) row[0];
            String txt = row[1] != null ? String.valueOf(row[1]) : null;
            if (txt == null || txt.isBlank()) continue;
            if (tipoPorPregunta.get(pid) == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) continue;
            txtByPid.computeIfAbsent(pid, k -> new ArrayList<>()).add(txt);
        }
        List<Map<String, Object>> respuestasTexto = new ArrayList<>();
        for (Map.Entry<Long, List<String>> en : txtByPid.entrySet()) {
            Map<String, Object> r = new HashMap<>();
            r.put("preguntaId", en.getKey());
            r.put("preguntaTexto", textoPregunta.getOrDefault(en.getKey(), "—"));
            r.put("respuestas", en.getValue());
            respuestasTexto.add(r);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("formulario", formularioMap);
        out.put("puedeVerResultados", true);
        out.put("horarioBloqueId", horarioBloqueId);
        out.put("docente", Map.of("id", maestro.getId(), "mensaje", "Los resultados son anónimos: solo promedios por reactivo; no se identifica al estudiante."));
        out.put("estadisticas", estadisticas);
        out.put("respuestasTexto", respuestasTexto);
        out.put("totalRespuestasAlumno", totalEval != null ? totalEval : 0);
        return out;
    }

    /**
     * Detalle para elaborar el informe institucional: respuestas de la evaluación académica, observaciones por bloque,
     * agregados anónimos de alumnos en la clase observada y valores Likert de autoevaluación del docente en ese horario.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> informeAcademicoDetalleParaAdministrativo(
            Usuario u, Long formularioAcademicoId, Long maestroId, Long horarioBloqueId) {
        assertUsuarioPuedeGestionarEvaluacionAcademicaMaestro(u, maestroId);
        assertUsuarioPuedeGestionarEvaluacionAcademicaHorario(u, horarioBloqueId);
        maestroRepository.findById(maestroId).orElseThrow(() -> new IllegalArgumentException("Docente no encontrado."));
        HorarioBloque horarioEntrada = horarioBloqueRepository.findById(horarioBloqueId)
                .orElseThrow(() -> new IllegalArgumentException("Bloque de horario no encontrado."));
        if (horarioEntrada.getMaestro() == null || !maestroId.equals(horarioEntrada.getMaestro().getId())) {
            throw new IllegalArgumentException("El docente no imparte la clase indicada.");
        }
        List<EvaluacionDocenteRespuesta> evs = respuestaRepository.findEvaluacionesAcademicasPorFormularioMaestroYHorario(
                formularioAcademicoId, maestroId, horarioBloqueId);
        if (evs.isEmpty()) {
            Map<String, Object> vacio = new LinkedHashMap<>();
            vacio.put("ok", false);
            vacio.put("puedeEditarInforme", false);
            vacio.put("mensaje", "No hay evaluación académica completada para esta clase. El informe solo se elabora después de registrar la visita y las respuestas para esta asignatura/módulo.");
            vacio.put("limiteCaracteresInforme", INFORME_PARA_DOCENTE_MAX_CARACTERES);
            return vacio;
        }
        Long rid = evs.get(0).getId();
        EvaluacionDocenteRespuesta r = respuestaRepository.findByIdWithItems(rid)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));
        // Cargar observaciones por bloque en una consulta aparte (no combinar con items en un solo EntityGraph).
        if (r.getObservacionesBloque() != null) {
            r.getObservacionesBloque().size();
        }
        EvaluacionDocenteFormulario facad = formularioRepository.findByIdWithPreguntas(formularioAcademicoId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("puedeEditarInforme", true);
        out.put("limiteCaracteresInforme", INFORME_PARA_DOCENTE_MAX_CARACTERES);
        out.put("formularioAcademico", Map.of(
                "id", facad.getId(),
                "titulo", facad.getTitulo() != null ? facad.getTitulo() : ""));
        out.put("respuestaAcademicaId", r.getId());
        out.put("informeParaDocenteActual", r.getInformeParaDocente());
        out.put("informeLeidoEn", r.getInformeLeidoEn());
        boolean tieneInformeDocente = r.getInformeParaDocente() != null && !r.getInformeParaDocente().trim().isEmpty();
        out.put("docenteLeidoInforme", tieneInformeDocente && r.getInformeLeidoEn() != null);
        out.put("fechaObservacion", r.getFechaVisita());
        out.put("nombreEvaluador", etiquetaEvaluadorAcademico(r.getEvaluadorUsuario()));

        HorarioBloque hb = r.getHorarioBloque();
        if (hb != null) {
            out.put("claseObservada", Map.of(
                    "horarioBloqueId", hb.getId(),
                    "asignaturaNombre", hb.getAsignatura() != null ? hb.getAsignatura().getNombre() : "—",
                    "grupoNombre", hb.getGrupoEntity() != null ? hb.getGrupoEntity().getNombre()
                            : (hb.getGrupoNombre() != null ? hb.getGrupoNombre() : "—")
            ));
        } else {
            out.put("claseObservada", null);
        }

        List<Map<String, Object>> itemsOut = new ArrayList<>();
        for (EvaluacionDocenteItem it : r.getItems()) {
            if (it.getPregunta() == null) continue;
            EvaluacionDocentePregunta pr = it.getPregunta();
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("preguntaId", pr.getId());
            im.put("texto", pr.getTexto());
            im.put("bloque", pr.getBloque() != null ? pr.getBloque() : "");
            im.put("orden", pr.getOrden());
            im.put("tipo", pr.getTipo() != null ? pr.getTipo().name() : null);
            if (pr.getTipo() == EvaluacionDocentePregunta.TipoPregunta.LIKERT_5) {
                im.put("valor", it.getValor());
            } else {
                im.put("valorTexto", it.getValorTexto());
            }
            itemsOut.add(im);
        }
        out.put("respuestasEvaluacionAcademica", itemsOut);

        List<Map<String, String>> obsOut = new ArrayList<>();
        if (r.getObservacionesBloque() != null) {
            for (EvaluacionDocenteObservacionBloque ob : r.getObservacionesBloque()) {
                if (ob == null) continue;
                Map<String, String> om = new LinkedHashMap<>();
                om.put("bloque", ob.getBloque() != null ? ob.getBloque() : "");
                om.put("texto", ob.getTexto() != null ? ob.getTexto() : "");
                obsOut.add(om);
            }
        }
        out.put("observacionesPorBloque", obsOut);

        Optional<EvaluacionDocenteFormulario> fAlumnoOpt = obtenerFormularioVigenteParaAlumno();
        Long horarioId = hb != null ? hb.getId() : null;
        Map<Long, String> textoAlumnoPorPid = new HashMap<>();
        Long faId = null;
        if (fAlumnoOpt.isPresent()) {
            EvaluacionDocenteFormulario faFull =
                    formularioRepository.findByIdWithPreguntas(fAlumnoOpt.get().getId()).orElse(fAlumnoOpt.get());
            faId = faFull.getId();
            if (faFull.getPreguntas() != null) {
                for (EvaluacionDocentePregunta p : faFull.getPreguntas()) {
                    if (p != null && p.getId() != null) {
                        textoAlumnoPorPid.put(p.getId(), p.getTexto() != null ? p.getTexto() : "");
                    }
                }
            }
        }

        if (faId != null && horarioId != null) {
            List<Object[]> likertRows =
                    itemRepository.estadisticasAnonimasPorMaestroFormularioYBloque(maestroId, faId, horarioId);
            List<Map<String, Object>> est = new ArrayList<>();
            for (Object[] row : likertRows) {
                Long pid = row[0] != null ? ((Number) row[0]).longValue() : null;
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("preguntaId", pid);
                e.put("preguntaTexto", pid != null ? textoAlumnoPorPid.getOrDefault(pid, "—") : "—");
                e.put("promedio", row[1] != null ? Math.round(((Number) row[1]).doubleValue() * 100.0) / 100.0 : null);
                e.put("numeroEvaluaciones", row[2] != null ? ((Number) row[2]).longValue() : 0L);
                est.add(e);
            }
            out.put("promediosEstudiantesLikert", est);

            List<Object[]> txtRows =
                    itemRepository.respuestasTextoAnonimasPorMaestroFormularioYBloque(maestroId, faId, horarioId);
            Map<Long, List<String>> byP = new LinkedHashMap<>();
            for (Object[] row : txtRows) {
                Long pid = row[0] != null ? ((Number) row[0]).longValue() : null;
                String tx = row[1] != null ? String.valueOf(row[1]) : "";
                if (pid != null) {
                    byP.computeIfAbsent(pid, k -> new ArrayList<>()).add(tx);
                }
            }
            List<Map<String, Object>> txtOut = new ArrayList<>();
            for (Map.Entry<Long, List<String>> en : byP.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("preguntaId", en.getKey());
                row.put("preguntaTexto", textoAlumnoPorPid.getOrDefault(en.getKey(), "—"));
                row.put("respuestas", en.getValue());
                txtOut.add(row);
            }
            out.put("respuestasTextoEstudiantes", txtOut);
        } else {
            out.put("promediosEstudiantesLikert", List.of());
            out.put("respuestasTextoEstudiantes", List.of());
        }

        Optional<EvaluacionDocenteFormulario> fAutoOpt = obtenerFormularioVigenteParaAutoevaluacion();
        Maestro mObj = maestroRepository.findById(maestroId).orElse(null);
        Usuario uDoc = mObj != null ? mObj.getUsuario() : null;
        Map<Long, String> textoAutoPorPid = new HashMap<>();
        if (fAutoOpt.isPresent()) {
            EvaluacionDocenteFormulario foFull =
                    formularioRepository.findByIdWithPreguntas(fAutoOpt.get().getId()).orElse(fAutoOpt.get());
            out.put("formularioAutoevaluacionTitulo", foFull.getTitulo());
            if (foFull.getPreguntas() != null) {
                for (EvaluacionDocentePregunta p : foFull.getPreguntas()) {
                    if (p != null && p.getId() != null) {
                        textoAutoPorPid.put(p.getId(), p.getTexto() != null ? p.getTexto() : "");
                    }
                }
            }
        } else {
            out.put("formularioAutoevaluacionTitulo", null);
        }

        if (fAutoOpt.isPresent() && uDoc != null && uDoc.getId() != null && horarioId != null) {
            List<Object[]> autoRows = itemRepository.findAutoevalLikertValoresPorUsuarioYHorario(uDoc.getId(), horarioId);
            List<Map<String, Object>> autoOut = new ArrayList<>();
            for (Object[] ar : autoRows) {
                Long pid = ar[0] != null ? ((Number) ar[0]).longValue() : null;
                Map<String, Object> ao = new LinkedHashMap<>();
                ao.put("preguntaId", pid);
                ao.put("preguntaTexto", pid != null ? textoAutoPorPid.getOrDefault(pid, "—") : "—");
                ao.put("orden", ar[1]);
                ao.put("valor", ar[2]);
                autoOut.add(ao);
            }
            out.put("autoevaluacionLikert", autoOut);
        } else {
            out.put("autoevaluacionLikert", List.of());
        }

        return out;
    }

    @Transactional
    public Map<String, Object> actualizarInformeAcademicoParaDocente(
            Usuario u, Long formularioAcademicoId, Long maestroId, Long horarioBloqueId, String texto) {
        assertUsuarioPuedeGestionarEvaluacionAcademicaMaestro(u, maestroId);
        assertUsuarioPuedeGestionarEvaluacionAcademicaHorario(u, horarioBloqueId);
        HorarioBloque hbCheck = horarioBloqueRepository.findById(horarioBloqueId)
                .orElseThrow(() -> new IllegalArgumentException("Bloque de horario no encontrado."));
        if (hbCheck.getMaestro() == null || !maestroId.equals(hbCheck.getMaestro().getId())) {
            throw new IllegalArgumentException("El docente no imparte la clase indicada.");
        }
        String t = texto != null ? texto : "";
        if (t.length() > INFORME_PARA_DOCENTE_MAX_CARACTERES) {
            throw new IllegalArgumentException(
                    "El informe no puede superar " + INFORME_PARA_DOCENTE_MAX_CARACTERES + " caracteres.");
        }
        List<EvaluacionDocenteRespuesta> lista =
                respuestaRepository.findEvaluacionesAcademicasPorFormularioMaestroYHorario(
                        formularioAcademicoId, maestroId, horarioBloqueId);
        if (lista.isEmpty()) {
            throw new IllegalArgumentException("No hay evaluación académica registrada para esta clase.");
        }
        EvaluacionDocenteRespuesta resp = lista.get(0);
        String anterior = resp.getInformeParaDocente();
        String nuevo = t.isBlank() ? null : t;
        resp.setInformeParaDocente(nuevo);
        if (nuevo != null) {
            String antNorm = anterior != null ? anterior.trim() : "";
            String nueNorm = nuevo.trim();
            if (!antNorm.equals(nueNorm)) {
                resp.setInformeLeidoEn(null);
            }
        }
        respuestaRepository.save(resp);
        Map<String, Object> out = new HashMap<>();
        out.put("message", "Informe guardado.");
        out.put("limiteCaracteresInforme", INFORME_PARA_DOCENTE_MAX_CARACTERES);
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarInformesAcademicosParaDocente(Maestro maestro) {
        if (maestro == null || maestro.getId() == null) {
            return List.of();
        }
        List<EvaluacionDocenteRespuesta> lista =
                respuestaRepository.findEvaluacionesAcademicasPorMaestroEvaluado(maestro.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvaluacionDocenteRespuesta r : lista) {
            if (r.getFormulario() == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("respuestaAcademicaId", r.getId());
            row.put("formularioId", r.getFormulario().getId());
            row.put("formularioTitulo", r.getFormulario().getTitulo());
            row.put("fechaObservacion", r.getFechaVisita());
            row.put("informeParaDocente", r.getInformeParaDocente());
            boolean tieneInforme = r.getInformeParaDocente() != null && !r.getInformeParaDocente().trim().isEmpty();
            row.put("informeSinLeer", tieneInforme && r.getInformeLeidoEn() == null);
            row.put("nombreEvaluador", etiquetaEvaluadorAcademico(r.getEvaluadorUsuario()));
            HorarioBloque h = r.getHorarioBloque();
            if (h != null) {
                row.put("horarioBloqueId", h.getId());
                row.put("asignaturaNombre", h.getAsignatura() != null ? h.getAsignatura().getNombre() : "—");
                row.put("grupoNombre", h.getGrupoEntity() != null ? h.getGrupoEntity().getNombre()
                        : (h.getGrupoNombre() != null ? h.getGrupoNombre() : "—"));
            } else {
                row.put("horarioBloqueId", null);
                row.put("asignaturaNombre", "—");
                row.put("grupoNombre", "—");
            }
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumenInformesAcademicosParaMaestro(Maestro maestro) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (maestro == null || maestro.getId() == null) {
            out.put("sinLeer", 0L);
            return out;
        }
        long n = respuestaRepository.countInformesAcademicosSinLeerParaMaestro(maestro.getId());
        out.put("sinLeer", n);
        return out;
    }

    @Transactional
    public Map<String, Object> marcarInformeAcademicoLeidoParaMaestro(Maestro maestro, Long respuestaAcademicaId) {
        if (maestro == null || maestro.getId() == null || respuestaAcademicaId == null) {
            throw new IllegalArgumentException("Datos incompletos.");
        }
        EvaluacionDocenteRespuesta r = respuestaRepository.findById(respuestaAcademicaId)
                .orElseThrow(() -> new IllegalArgumentException("Informe no encontrado."));
        if (r.getMaestroEvaluado() == null || !maestro.getId().equals(r.getMaestroEvaluado().getId())) {
            throw new IllegalArgumentException("No autorizado.");
        }
        if (r.getFormulario() == null
                || r.getFormulario().getTipo() != EvaluacionDocenteFormulario.TipoEvaluacion.POR_SECRETARIA_ACADEMICA) {
            throw new IllegalArgumentException("Evaluación no válida.");
        }
        String inf = r.getInformeParaDocente();
        if (inf == null || inf.trim().isEmpty()) {
            throw new IllegalArgumentException("No hay texto de informe para registrar lectura.");
        }
        r.setInformeLeidoEn(LocalDateTime.now());
        respuestaRepository.save(r);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Lectura registrada.");
        out.put("sinLeerRestantes", respuestaRepository.countInformesAcademicosSinLeerParaMaestro(maestro.getId()));
        return out;
    }

    @Transactional
    public void eliminarFormulario(Long formularioId) {
        if (formularioId == null) {
            throw new IllegalArgumentException("formularioId es obligatorio.");
        }
        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        if (respuestaRepository.existsByFormulario_Id(formularioId)) {
            throw new IllegalArgumentException(
                    "Este formulario ya tiene respuestas registradas. No se puede eliminar; desactivalo y crea una nueva version si necesitas cambios.");
        }
        try { itemRepository.deleteByFormularioId(formularioId); } catch (Exception ignored) {}
        formularioRepository.delete(f);
    }

    @Transactional(readOnly = true)
    public byte[] generarPlantillaExcel() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sForm = wb.createSheet("Formulario");
            int r = 0;
            Row h = sForm.createRow(r++);
            h.createCell(0).setCellValue("campo");
            h.createCell(1).setCellValue("valor");
            Row rTipo = sForm.createRow(r++);
            rTipo.createCell(0).setCellValue("tipo");
            rTipo.createCell(1).setCellValue("POR_ALUMNOS");
            Row rTit = sForm.createRow(r++);
            rTit.createCell(0).setCellValue("titulo");
            rTit.createCell(1).setCellValue("");
            Row rDesc = sForm.createRow(r++);
            rDesc.createCell(0).setCellValue("descripcion");
            rDesc.createCell(1).setCellValue("");
            Row rAct = sForm.createRow(r++);
            rAct.createCell(0).setCellValue("activo");
            rAct.createCell(1).setCellValue("true");
            sForm.autoSizeColumn(0);
            sForm.autoSizeColumn(1);

            Sheet sQ = wb.createSheet("Preguntas");
            Row qh = sQ.createRow(0);
            qh.createCell(0).setCellValue("bloque");
            qh.createCell(1).setCellValue("tipo");
            qh.createCell(2).setCellValue("orden");
            qh.createCell(3).setCellValue("texto");
            qh.createCell(4).setCellValue("opciones");
            // ejemplo
            Row ex = sQ.createRow(1);
            ex.createCell(0).setCellValue("Bloque 1");
            ex.createCell(1).setCellValue("LIKERT_5");
            ex.createCell(2).setCellValue(0);
            ex.createCell(3).setCellValue("Explica con claridad los temas.");
            ex.createCell(4).setCellValue("");
            for (int c = 0; c <= 4; c++) sQ.autoSizeColumn(c);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo generar la plantilla Excel.");
        }
    }

    @Transactional(readOnly = true)
    public byte[] generarExcelDeFormulario(Long formularioId) {
        if (formularioId == null) {
            throw new IllegalArgumentException("formularioId es obligatorio.");
        }
        EvaluacionDocenteFormulario f = formularioRepository.findByIdWithPreguntas(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado."));
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sForm = wb.createSheet("Formulario");
            int r = 0;
            Row h = sForm.createRow(r++);
            h.createCell(0).setCellValue("campo");
            h.createCell(1).setCellValue("valor");

            Row rTipo = sForm.createRow(r++);
            rTipo.createCell(0).setCellValue("tipo");
            rTipo.createCell(1).setCellValue(f.getTipo() != null ? f.getTipo().name() : "POR_ALUMNOS");

            Row rTit = sForm.createRow(r++);
            rTit.createCell(0).setCellValue("titulo");
            rTit.createCell(1).setCellValue(f.getTitulo() != null ? f.getTitulo() : "");

            Row rDesc = sForm.createRow(r++);
            rDesc.createCell(0).setCellValue("descripcion");
            rDesc.createCell(1).setCellValue(f.getDescripcion() != null ? f.getDescripcion() : "");

            Row rAct = sForm.createRow(r++);
            rAct.createCell(0).setCellValue("activo");
            rAct.createCell(1).setCellValue(Boolean.TRUE.equals(f.getActivo()) ? "true" : "false");

            sForm.autoSizeColumn(0);
            sForm.autoSizeColumn(1);

            Sheet sQ = wb.createSheet("Preguntas");
            Row qh = sQ.createRow(0);
            qh.createCell(0).setCellValue("bloque");
            qh.createCell(1).setCellValue("tipo");
            qh.createCell(2).setCellValue("orden");
            qh.createCell(3).setCellValue("texto");
            qh.createCell(4).setCellValue("opciones");

            List<EvaluacionDocentePregunta> preguntas = f.getPreguntas() != null ? f.getPreguntas() : List.of();
            preguntas = preguntas.stream()
                    .sorted(Comparator.comparing(EvaluacionDocentePregunta::getOrden, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(EvaluacionDocentePregunta::getId))
                    .toList();

            int qr = 1;
            for (EvaluacionDocentePregunta p : preguntas) {
                Row row = sQ.createRow(qr++);
                row.createCell(0).setCellValue(p.getBloque() != null ? p.getBloque() : "Bloque 1");
                row.createCell(1).setCellValue(p.getTipo() != null ? p.getTipo().name() : "LIKERT_5");
                row.createCell(2).setCellValue(p.getOrden() != null ? p.getOrden() : (qr - 2));
                row.createCell(3).setCellValue(p.getTexto() != null ? p.getTexto() : "");
                // Guardar opciones en un solo campo; el import soporta | o saltos de línea
                String ops = p.getOpciones() != null ? p.getOpciones() : "";
                if (!ops.isBlank()) {
                    ops = Arrays.stream(ops.split("\\r?\\n"))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.joining("|"));
                }
                row.createCell(4).setCellValue(ops);
            }
            for (int c = 0; c <= 4; c++) sQ.autoSizeColumn(c);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo generar el Excel del formulario.");
        }
    }

    @Transactional
    public void importarDesdeExcel(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Archivo Excel vacío.");
        }
        try (Workbook wb = new XSSFWorkbook(archivo.getInputStream())) {
            Sheet sForm = wb.getSheet("Formulario");
            Sheet sQ = wb.getSheet("Preguntas");
            if (sForm == null || sQ == null) {
                throw new IllegalArgumentException("El Excel debe incluir hojas: Formulario y Preguntas.");
            }
            Map<String, String> kv = new HashMap<>();
            for (int i = 1; i <= sForm.getLastRowNum(); i++) {
                Row row = sForm.getRow(i);
                if (row == null) continue;
                String k = cellStr(row.getCell(0));
                String v = cellStr(row.getCell(1));
                if (k != null && !k.isBlank()) kv.put(k.trim().toLowerCase(), v != null ? v.trim() : "");
            }
            EvaluacionDocenteCrearFormularioRequest req = new EvaluacionDocenteCrearFormularioRequest();
            req.setTipo(kv.getOrDefault("tipo", "POR_ALUMNOS"));
            req.setTitulo(kv.getOrDefault("titulo", "").trim());
            req.setDescripcion(vacioANull(kv.get("descripcion")));
            req.setActivo(parseBool(kv.getOrDefault("activo", "true")));
            req.setFechaInicio(null);
            req.setFechaFin(null);

            List<EvaluacionDocenteCrearFormularioRequest.Bloque> bloques = new ArrayList<>();
            Map<String, EvaluacionDocenteCrearFormularioRequest.Bloque> porBloque = new LinkedHashMap<>();
            for (int i = 1; i <= sQ.getLastRowNum(); i++) {
                Row row = sQ.getRow(i);
                if (row == null) continue;
                String bloque = vacioANull(cellStr(row.getCell(0)));
                String tipo = vacioANull(cellStr(row.getCell(1)));
                String ordenS = vacioANull(cellStr(row.getCell(2)));
                String texto = vacioANull(cellStr(row.getCell(3)));
                String opciones = vacioANull(cellStr(row.getCell(4)));
                if (texto == null) continue;
                String bName = (bloque == null) ? "Bloque 1" : bloque;
                EvaluacionDocenteCrearFormularioRequest.Bloque b = porBloque.get(bName);
                if (b == null) {
                    b = new EvaluacionDocenteCrearFormularioRequest.Bloque();
                    b.setTitulo(bName);
                    b.setOrden(porBloque.size());
                    b.setPreguntas(new ArrayList<>());
                    porBloque.put(bName, b);
                }
                EvaluacionDocenteCrearFormularioRequest.Pregunta p = new EvaluacionDocenteCrearFormularioRequest.Pregunta();
                p.setTexto(texto);
                p.setTipo(tipo != null ? tipo.toUpperCase() : "LIKERT_5");
                try { p.setOrden(ordenS != null ? Integer.parseInt(ordenS) : b.getPreguntas().size()); } catch (Exception e) { p.setOrden(b.getPreguntas().size()); }
                if ("OPCION_MULTIPLE".equalsIgnoreCase(p.getTipo())) {
                    List<String> ops = new ArrayList<>();
                    if (opciones != null) {
                        // opciones separadas por | o por saltos
                        String[] parts = opciones.contains("|") ? opciones.split("\\|") : opciones.split("\\r?\\n");
                        for (String part : parts) {
                            if (part != null && !part.trim().isBlank()) ops.add(part.trim());
                        }
                    }
                    p.setOpciones(ops);
                }
                b.getPreguntas().add(p);
            }
            bloques.addAll(porBloque.values());
            req.setBloques(bloques);
            if (req.getTitulo() == null || req.getTitulo().isBlank()) {
                throw new IllegalArgumentException("Falta titulo en la hoja Formulario.");
            }
            crearFormulario(req);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el Excel. Verifica que sea una plantilla válida.");
        }
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Boolean parseBool(String s) {
        if (s == null) return true;
        String t = s.trim().toLowerCase();
        if (t.equals("false") || t.equals("0") || t.equals("no")) return false;
        return true;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); } catch (Exception e) { return null; }
    }

    private static String cellStr(Cell c) {
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) {
                // manejar orden numérico
                double d = c.getNumericCellValue();
                long l = Math.round(d);
                if (Math.abs(d - l) < 0.00001) return String.valueOf(l);
                return String.valueOf(d);
            }
            if (c.getCellType() == CellType.BOOLEAN) {
                return c.getBooleanCellValue() ? "true" : "false";
            }
            c.setCellType(CellType.STRING);
            return c.getStringCellValue();
        } catch (Exception e) {
            return null;
        }
    }
}
