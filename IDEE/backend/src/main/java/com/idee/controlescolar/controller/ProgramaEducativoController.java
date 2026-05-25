package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Plantel;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.PlantelRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.CargaMasivaProgramasAsignaturasService;
import com.idee.controlescolar.service.PeriodoService;
import com.idee.controlescolar.service.ProgramaAccesoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para gestionar programas educativos
 */
@RestController
@RequestMapping("/programas-educativos")
@CrossOrigin(origins = "*")
public class ProgramaEducativoController {

    @Autowired
    private ProgramaEducativoRepository programaEducativoRepository;

    @Autowired
    private PeriodoService periodoService;

    @Autowired
    private CargaMasivaProgramasAsignaturasService cargaMasivaService;

    @Autowired
    private ProgramaAccesoService programaAccesoService;

    @Autowired
    private PlantelRepository plantelRepository;

    private void enriquecerNombrePlantel(ProgramaEducativo programa) {
        if (programa == null) {
            return;
        }
        programa.setNombrePlantel(null);
        String clave = programa.getClaveDgp();
        if (clave == null || clave.isBlank()) {
            return;
        }
        String c = clave.trim();
        Optional<Plantel> opt = plantelRepository.findByClaveDgp(c);
        if (opt.isEmpty()) {
            opt = plantelRepository.findByClaveDgpIgnoreCaseTrim(c);
        }
        opt.ifPresent(pl -> programa.setNombrePlantel(pl.getNombrePlantel()));
    }

    private void enriquecerNombrePlantelLista(List<ProgramaEducativo> programas) {
        if (programas == null) {
            return;
        }
        for (ProgramaEducativo p : programas) {
            enriquecerNombrePlantel(p);
        }
    }

    /**
     * Obtener todos los programas educativos
     */
    @GetMapping
    @RequierePermiso("VER_PROGRAMAS")
    public ResponseEntity<List<ProgramaEducativo>> obtenerTodos(Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var ids = programaAccesoService.programaIdsPermitidos(u);
            if (ids.isEmpty()) return ResponseEntity.ok(List.of());
            List<ProgramaEducativo> programas = programaEducativoRepository.findByIdInOrderByNombreAsc(ids);
            enriquecerNombrePlantelLista(programas);
            return ResponseEntity.ok(programas);
        }
        List<ProgramaEducativo> programas = programaEducativoRepository.findAllByOrderByNombreAsc();
        enriquecerNombrePlantelLista(programas);
        return ResponseEntity.ok(programas);
    }

    /**
     * Obtener un programa educativo por ID
     */
    @GetMapping("/{id}")
    @RequierePermiso("VER_PROGRAMAS")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(id);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ProgramaEducativo pr = programaOpt.get();
        enriquecerNombrePlantel(pr);
        return ResponseEntity.ok(pr);
    }

    /**
     * Obtiene el plantel asociado al programa (para encabezados PDF de constancias).
     * La relación se resuelve por claveDgp del programa.
     */
    @GetMapping("/{id}/plantel")
    @RequierePermiso("VER_CONSTANCIAS")
    public ResponseEntity<?> obtenerPlantelDePrograma(@PathVariable Long id) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(id);
        if (programaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ProgramaEducativo pr = programaOpt.get();
        String claveDgp = pr.getClaveDgp() != null ? pr.getClaveDgp().trim() : "";
        if (claveDgp.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "El programa no tiene plantel asociado (claveDgp vacía)."));
        }
        return plantelRepository.findByClaveDgp(claveDgp)
                .or(() -> plantelRepository.findByClaveDgpIgnoreCaseTrim(claveDgp))
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(Map.of(
                        "plantelId", p.getId(),
                        "nombrePlantel", p.getNombrePlantel(),
                        "claveCct", p.getClaveCct(),
                        "claveDgp", p.getClaveDgp()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "No se encontró plantel para la claveDgp: " + claveDgp
                )));
    }

    /**
     * Obtener un programa educativo por clave
     */
    @GetMapping("/clave/{clave}")
    @RequierePermiso("VER_PROGRAMAS")
    public ResponseEntity<?> obtenerPorClave(@PathVariable String clave) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findByClave(clave);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ProgramaEducativo pr = programaOpt.get();
        enriquecerNombrePlantel(pr);
        return ResponseEntity.ok(pr);
    }

    /**
     * Crear un nuevo programa educativo
     */
    @PostMapping
    @RequierePermiso("ACTUALIZAR_PROGRAMAS")
    public ResponseEntity<?> crear(@RequestBody ProgramaEducativo programa) {
        try {
            if (programa.getCreditosTotales() == null || programa.getCreditosTotales() <= 0) {
                return ResponseEntity.badRequest()
                        .body("Créditos totales es obligatorio y debe ser mayor a 0");
            }
            if (programa.getClave() == null || programa.getClave().isBlank()) {
                return ResponseEntity.badRequest().body("La clave del programa es obligatoria.");
            }
            if (programaEducativoRepository.existsByClave(programa.getClave().trim())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Ya existe un programa con la clave: " + programa.getClave());
            }
            if (programa.getIdPrograma() != null && !programa.getIdPrograma().isBlank()
                    && programaEducativoRepository.existsByIdPrograma(programa.getIdPrograma().trim())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Ya existe un programa con el ID Programa: " + programa.getIdPrograma());
            }

            ProgramaEducativo programaGuardado = programaEducativoRepository.save(programa);
            periodoService.asegurarPeriodosParaPrograma(programaGuardado);
            enriquecerNombrePlantel(programaGuardado);
            return ResponseEntity.status(HttpStatus.CREATED).body(programaGuardado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el programa: " + e.getMessage());
        }
    }

    /**
     * Actualizar un programa educativo existente
     */
    @PutMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_PROGRAMAS")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody ProgramaEducativo programaActualizado) {
        if (programaActualizado.getCreditosTotales() == null || programaActualizado.getCreditosTotales() <= 0) {
            return ResponseEntity.badRequest()
                    .body("Créditos totales es obligatorio y debe ser mayor a 0");
        }
        if (programaActualizado.getClave() == null || programaActualizado.getClave().isBlank()) {
            return ResponseEntity.badRequest().body("La clave del programa es obligatoria.");
        }
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(id);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        String nuevaClave = programaActualizado.getClave().trim();
        if (programaEducativoRepository.existsByClaveAndIdNot(nuevaClave, id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Ya existe otro programa con la clave: " + nuevaClave);
        }
        String nuevoIdPrograma = programaActualizado.getIdPrograma();
        if (nuevoIdPrograma != null && !nuevoIdPrograma.isBlank()
                && programaEducativoRepository.existsByIdProgramaAndIdNot(nuevoIdPrograma.trim(), id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Ya existe otro programa con el ID Programa: " + nuevoIdPrograma);
        }
        ProgramaEducativo programa = programaOpt.get();
        programa.setIdPrograma(programaActualizado.getIdPrograma());
        programa.setClave(programaActualizado.getClave());
        programa.setClaveDgp(programaActualizado.getClaveDgp());
        programa.setNombre(programaActualizado.getNombre());
        programa.setTipoPrograma(programaActualizado.getTipoPrograma());
        programa.setDuracionPeriodos(programaActualizado.getDuracionPeriodos());
        programa.setTipoPeriodo(programaActualizado.getTipoPeriodo());
        programa.setModalidad(programaActualizado.getModalidad());
        programa.setCreditosTotales(programaActualizado.getCreditosTotales());
        programa.setPlanEstudio(programaActualizado.getPlanEstudio());
        programa.setRvoe(programaActualizado.getRvoe());
        programa.setFechaRvoe(programaActualizado.getFechaRvoe());
        programa.setEstatus(programaActualizado.getEstatus());
        programa.setDescripcion(programaActualizado.getDescripcion());
        ProgramaEducativo programaGuardado = programaEducativoRepository.save(programa);
        periodoService.asegurarPeriodosParaPrograma(programaGuardado);
        enriquecerNombrePlantel(programaGuardado);
        return ResponseEntity.ok(programaGuardado);
    }

    /**
     * Carga masiva de programas y asignaturas desde Excel.
     * Formato: hoja "Programas" y hoja "Asignaturas" con columnas ID, ID Programa, Clave Programa, etc.
     * Las asignaturas se vinculan al programa mediante ID Programa o Clave Programa.
     */
    @PostMapping(value = "/carga-masiva", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("ACTUALIZAR_PROGRAMAS")
    public ResponseEntity<?> cargaMasiva(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se envió ningún archivo"));
        }
        String filename = archivo.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo debe ser Excel (.xlsx o .xls)"));
        }
        try {
            Map<String, Object> resultado = cargaMasivaService.procesarExcel(archivo);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar el archivo: " + e.getMessage()));
        }
    }

    /**
     * Eliminar un programa educativo
     */
    @DeleteMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_PROGRAMAS")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(id);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        programaEducativoRepository.delete(programaOpt.get());
        return ResponseEntity.ok().build();
    }
}
