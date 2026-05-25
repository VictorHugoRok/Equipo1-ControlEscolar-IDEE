package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.AlumnoPrograma;
import com.idee.controlescolar.model.Cohorte;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.CohorteRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.security.RequierePermiso;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cohortes")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CohorteController {

    private final CohorteRepository cohorteRepository;
    private final AlumnoRepository alumnoRepository;
    private final ProgramaEducativoRepository programaEducativoRepository;

    @GetMapping
    @RequierePermiso("VER_GRUPOS")
    public ResponseEntity<?> listar() {
        List<Cohorte> lista = cohorteRepository.findAllOrderByNombreAscIgnoreCase();
        Map<Long, Long> tamanoPorCohorte = alumnoRepository.countAlumnosGroupedByCohorteId().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a));

        List<Map<String, Object>> out = lista.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("idCohorte", c.getIdCohorte());
            row.put("nombre", c.getNombre());
            row.put("descripcion", c.getDescripcion() != null ? c.getDescripcion() : "");
            row.put("programaId", c.getPrograma() != null ? c.getPrograma().getId() : null);
            row.put("programaNombre", c.getPrograma() != null && c.getPrograma().getNombre() != null ? c.getPrograma().getNombre() : "");
            row.put("tamano", tamanoPorCohorte.getOrDefault(c.getId(), 0L).intValue());
            return row;
        }).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body) {
        String idCohorte = str(body.get("idCohorte"));
        String nombre = str(body.get("nombre"));
        String descripcion = emptyToNull(str(body.get("descripcion")));
        Long programaId = longVal(body.get("programaId"));

        if (idCohorte == null || idCohorte.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El ID de cohorte es obligatorio."));
        }
        if (nombre == null || nombre.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la cohorte es obligatorio."));
        }
        if (programaId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El programa es obligatorio para crear la cohorte."));
        }
        if (cohorteRepository.existsByIdCohorte(idCohorte.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "El ID de cohorte ya existe."));
        }
        ProgramaEducativo programa = programaEducativoRepository.findById(programaId).orElse(null);
        if (programa == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Programa educativo no encontrado."));
        }

        Cohorte c = new Cohorte();
        c.setPrograma(programa);
        c.setIdCohorte(idCohorte.trim());
        c.setNombre(nombre.trim());
        c.setDescripcion(descripcion);
        Cohorte guardada = cohorteRepository.save(c);

        // (Opcional) permitir enviar miembros durante la creación
        Set<Long> idsMiembros = parseIds(body.get("alumnoIds"));
        if (!idsMiembros.isEmpty()) {
            List<Alumno> alumnos = alumnoRepository.findAllByIdInWithProgramasYCohortes(idsMiembros);
            List<Alumno> validos = alumnos.stream()
                    .filter(a -> alumnoInscritoEnPrograma(a, programaId))
                    .toList();
            for (Alumno a : validos) a.getCohortes().add(guardada);
            if (!validos.isEmpty()) alumnoRepository.saveAll(validos);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Cohorte creada correctamente.",
                "id", guardada.getId()
        ));
    }

    @PutMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Cohorte c = cohorteRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        String idCohorte = str(body.get("idCohorte"));
        String nombre = str(body.get("nombre"));
        String descripcion = emptyToNull(str(body.get("descripcion")));
        Long programaId = longVal(body.get("programaId"));

        if (idCohorte == null || idCohorte.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El ID de cohorte es obligatorio."));
        }
        if (nombre == null || nombre.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la cohorte es obligatorio."));
        }
        if (programaId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El programa es obligatorio para actualizar la cohorte."));
        }
        if (cohorteRepository.existsByIdCohorteAndIdNot(idCohorte.trim(), id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El ID de cohorte ya existe en otra cohorte."));
        }
        if (c.getPrograma() == null || c.getPrograma().getId() == null || !c.getPrograma().getId().equals(programaId)) {
            // Evitar inconsistencias: si hay miembros, no permitir cambiar de programa.
            long miembros = alumnoRepository.countByCohortes_Id(id);
            if (miembros > 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "No puedes cambiar el programa de una cohorte con miembros asignados. Primero desasigna a los alumnos."));
            }
            ProgramaEducativo programa = programaEducativoRepository.findById(programaId).orElse(null);
            if (programa == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Programa educativo no encontrado."));
            }
            c.setPrograma(programa);
        }

        c.setIdCohorte(idCohorte.trim());
        c.setNombre(nombre.trim());
        c.setDescripcion(descripcion);
        cohorteRepository.save(c);
        return ResponseEntity.ok(Map.of("message", "Cohorte actualizada correctamente."));
    }

    @DeleteMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Cohorte c = cohorteRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        List<Alumno> miembros = alumnoRepository.findByCohortes_IdOrderByApellidoPaternoAsc(id);
        miembros.forEach(a -> a.getCohortes().removeIf(x -> x != null && Objects.equals(x.getId(), id)));
        if (!miembros.isEmpty()) alumnoRepository.saveAll(miembros);

        cohorteRepository.delete(c);
        return ResponseEntity.ok(Map.of("message", "Cohorte eliminada correctamente."));
    }

    @GetMapping("/{id}/miembros")
    @RequierePermiso({"VER_GRUPOS", "VER_ALUMNOS"})
    public ResponseEntity<?> miembros(@PathVariable Long id) {
        Cohorte c = cohorteRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        Long programaIdCohorte = c.getPrograma() != null ? c.getPrograma().getId() : null;
        List<Map<String, Object>> out = alumnoRepository.findByCohortes_IdOrderByApellidoPaternoAsc(id).stream()
                .map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", a.getId());
                    row.put("matricula", a.getMatricula() != null ? a.getMatricula() : "");
                    row.put("nombre", a.getNombreCompleto());
                    row.put("periodoCursando", a.getPeriodoCursando() != null ? a.getPeriodoCursando() : 0);
                    // Mostrar el programa que coincide con la cohorte (si el alumno lo tiene asignado).
                    ProgramaEducativo progMatch = null;
                    if (programaIdCohorte != null && a.getProgramasAsignados() != null) {
                        for (AlumnoPrograma ap : a.getProgramasAsignados()) {
                            if (ap == null || ap.getPrograma() == null || ap.getPrograma().getId() == null) continue;
                            if (programaIdCohorte.equals(ap.getPrograma().getId())) {
                                progMatch = ap.getPrograma();
                                break;
                            }
                        }
                    }
                    ProgramaEducativo progMostrar = (progMatch != null) ? progMatch : a.getPrograma();
                    row.put("programaId", progMostrar != null ? progMostrar.getId() : null);
                    row.put("programaNombre", progMostrar != null && progMostrar.getNombre() != null ? progMostrar.getNombre() : "");
                    return row;
                }).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{id}/candidatos")
    @RequierePermiso({"VER_GRUPOS", "VER_ALUMNOS"})
    public ResponseEntity<?> candidatos(@PathVariable Long id) {
        Cohorte c = cohorteRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        // Candidatos relevantes: alumnos del mismo programa que la cohorte (y sus cohortes para mostrar si ya están asignados).
        Long programaId = c.getPrograma() != null ? c.getPrograma().getId() : null;
        String programaNombreCohorte = (c.getPrograma() != null && c.getPrograma().getNombre() != null) ? c.getPrograma().getNombre() : "";
        List<Alumno> alumnos = programaId != null
                ? alumnoRepository.findByProgramaIdOrderByApellidoPaternoAsc(programaId)
                : alumnoRepository.findAllWithProgramaYCohorteOrdered();
        List<Map<String, Object>> out = alumnos.stream()
                .map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", a.getId());
                    row.put("matricula", a.getMatricula() != null ? a.getMatricula() : "");
                    row.put("nombre", a.getNombreCompleto());
                    // Esta lista está filtrada por el programa de la cohorte; mostrar ese programa,
                    // incluso si el "legacy" getPrograma() devuelve otro.
                    if (programaId != null) {
                        String nombre = programaNombreCohorte;
                        if (a.getProgramasAsignados() != null) {
                            for (AlumnoPrograma ap : a.getProgramasAsignados()) {
                                if (ap == null || ap.getPrograma() == null || ap.getPrograma().getId() == null) continue;
                                if (programaId.equals(ap.getPrograma().getId())) {
                                    if (ap.getPrograma().getNombre() != null) nombre = ap.getPrograma().getNombre();
                                    break;
                                }
                            }
                        }
                        row.put("programaNombre", nombre != null ? nombre : "");
                    } else {
                        row.put("programaNombre", a.getPrograma() != null ? a.getPrograma().getNombre() : "");
                    }
                    List<Cohorte> cohortesAlumno = (a.getCohortes() == null)
                            ? List.of()
                            : a.getCohortes().stream().filter(Objects::nonNull).toList();
                    List<Map<String, Object>> cs = cohortesAlumno.stream()
                            .map(x -> Map.<String, Object>of(
                                    "id", x.getId(),
                                    "nombre", x.getNombre() != null && !x.getNombre().isBlank()
                                            ? x.getNombre()
                                            : (x.getIdCohorte() != null ? x.getIdCohorte() : "")
                            ))
                            .toList();
                    row.put("cohortes", cs);
                    row.put("cohorteIds", cohortesAlumno.stream().map(Cohorte::getId).toList());
                    row.put("cohortesNombres", cohortesAlumno.stream()
                            .map(x -> x.getNombre() != null && !x.getNombre().isBlank() ? x.getNombre() : x.getIdCohorte())
                            .filter(s -> s != null && !s.isBlank())
                            .toList());
                    return row;
                }).toList();
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{id}/miembros")
    @RequierePermiso({"ACTUALIZAR_ALUMNOS", "ACTUALIZAR_GRUPOS"})
    public ResponseEntity<?> guardarMiembros(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Cohorte c = cohorteRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        // Cambios explícitos (para evitar eliminaciones "automáticas"):
        // - alumnoIdsAdd (o alumnoIds legacy): alumnos a AGREGAR a esta cohorte
        // - alumnoIdsRemove: alumnos a QUITAR de esta cohorte
        Set<Long> idsAdd = new LinkedHashSet<>();
        idsAdd.addAll(parseIds(body.get("alumnoIdsAdd")));
        idsAdd.addAll(parseIds(body.get("alumnoIds"))); // compat con frontend viejo (solo agrega)
        Set<Long> idsRemove = parseIds(body.get("alumnoIdsRemove"));
        Long programaId = c.getPrograma() != null ? c.getPrograma().getId() : null;

        int removidos = 0;
        if (!idsRemove.isEmpty()) {
            List<Alumno> actuales = alumnoRepository.findAllByIdInWithProgramasYCohortes(idsRemove);
            for (Alumno a : actuales) {
                if (a == null) continue;
                // Quitar solo de esta cohorte (idsRemove son explícitos); no filtrar por programa “principal”.
                boolean cambio = a.getCohortes().removeIf(x -> x != null && Objects.equals(x.getId(), id));
                if (cambio) removidos++;
            }
            if (!actuales.isEmpty()) alumnoRepository.saveAll(actuales);
        }

        List<Alumno> nuevos = idsAdd.isEmpty() ? List.of() : alumnoRepository.findAllByIdInWithProgramasYCohortes(idsAdd);
        List<Alumno> nuevosValidos = (programaId != null)
                ? nuevos.stream().filter(a -> alumnoInscritoEnPrograma(a, programaId)).toList()
                : nuevos;
        int agregados = 0;
        for (Alumno a : nuevosValidos) {
            boolean cambio = a.getCohortes().add(c);
            if (cambio) agregados++;
        }
        if (!nuevosValidos.isEmpty()) alumnoRepository.saveAll(nuevosValidos);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Miembros de cohorte actualizados.");
        out.put("agregados", agregados);
        out.put("removidos", removidos);
        int ignoradosAdd = Math.max(0, idsAdd.size() - nuevosValidos.size());
        out.put("ignoradosAdd", ignoradosAdd);
        out.put("solicitadosAdd", idsAdd.size());
        out.put("solicitadosRemove", idsRemove.size());
        if (ignoradosAdd > 0) {
            out.put("advertencia",
                    "No se agregaron algunos alumnos: deben tener inscripción al programa educativo de esta cohorte (revisar alumno_programa).");
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Inscripción al programa: filas en {@code alumno_programa}, o columna legacy {@code alumnos.programa_id} si aplica.
     */
    private static boolean alumnoInscritoEnPrograma(Alumno a, Long programaId) {
        if (programaId == null || a == null) {
            return true;
        }
        if (a.getProgramasAsignados() != null) {
            for (AlumnoPrograma ap : a.getProgramasAsignados()) {
                if (ap != null && ap.getPrograma() != null && programaId.equals(ap.getPrograma().getId())) {
                    return true;
                }
            }
        }
        return a.getPrograma() != null && programaId.equals(a.getPrograma().getId());
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private String emptyToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s;
    }

    private Long longVal(Object o) {
        if (o == null) return null;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private Set<Long> parseIds(Object raw) {
        if (!(raw instanceof List<?> list)) return Set.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (Object o : list) {
            Long v = longVal(o);
            if (v != null) ids.add(v);
        }
        return ids;
    }
}
