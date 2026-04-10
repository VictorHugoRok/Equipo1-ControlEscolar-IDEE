package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.MaestroDocumento;
import com.idee.controlescolar.repository.MaestroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Controlador REST para gestionar maestros (docentes)
 */
@RestController
@RequestMapping("/api/maestros")
@CrossOrigin(origins = "*")
public class MaestroController {

    @Autowired
    private MaestroRepository maestroRepository;

    @GetMapping
    public ResponseEntity<List<Maestro>> obtenerTodos() {
        return ResponseEntity.ok(maestroRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Maestro> maestroOpt = maestroRepository.findById(id);
        if (!maestroOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(maestroOpt.get());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crear(
            @RequestPart("maestro") Maestro maestro,
            @RequestPart(value = "antecedentes", required = false) List<MultipartFile> antecedentes) {
        try {
            if (maestroRepository.existsByCurp(maestro.getCurp())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Ya existe un maestro con la CURP: " + maestro.getCurp());
            }

            Maestro maestroGuardado = maestroRepository.save(maestro);

            if (antecedentes != null && !antecedentes.isEmpty()) {
                for (MultipartFile archivo : antecedentes) {
                    if (archivo == null || archivo.isEmpty()) {
                        continue;
                    }
                    MaestroDocumento documento = new MaestroDocumento();
                    documento.setMaestro(maestroGuardado);
                    documento.setFilename(archivo.getOriginalFilename());
                    documento.setContentType(archivo.getContentType());
                    documento.setSizeBytes(archivo.getSize());
                    documento.setData(archivo.getBytes());
                    maestroGuardado.getDocumentos().add(documento);
                }
                maestroGuardado = maestroRepository.save(maestroGuardado);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(maestroGuardado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el maestro: " + e.getMessage());
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizar(
            @PathVariable Long id,
            @RequestPart("maestro") Maestro maestroActualizado,
            @RequestPart(value = "antecedentes", required = false) List<MultipartFile> antecedentes) {
        Optional<Maestro> maestroOpt = maestroRepository.findById(id);
        if (!maestroOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Maestro maestro = maestroOpt.get();
        try {
            if (maestroActualizado.getCurp() != null
                    && maestroRepository.existsByCurpAndIdNot(maestroActualizado.getCurp(), id)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Ya existe un maestro con la CURP: " + maestroActualizado.getCurp());
            }

            maestro.setCurp(maestroActualizado.getCurp());
            maestro.setNombre(maestroActualizado.getNombre());
            maestro.setApellidoPaterno(maestroActualizado.getApellidoPaterno());
            maestro.setApellidoMaterno(maestroActualizado.getApellidoMaterno());
            maestro.setEtiqueta(maestroActualizado.getEtiqueta());
            maestro.setCorreoInstitucional(maestroActualizado.getCorreoInstitucional());
            maestro.setCorreoPersonal(maestroActualizado.getCorreoPersonal());
            maestro.setTelefono(maestroActualizado.getTelefono());
            maestro.setCodigoPostal(maestroActualizado.getCodigoPostal());
            maestro.setGradoAcademico(maestroActualizado.getGradoAcademico());
            maestro.setCedulaProfesional(maestroActualizado.getCedulaProfesional());
            maestro.setArea(maestroActualizado.getArea());
            maestro.setRfc(maestroActualizado.getRfc());
            maestro.setRegimenFiscal(maestroActualizado.getRegimenFiscal());
            maestro.setTipoMaestro(maestroActualizado.getTipoMaestro());
            maestro.setFechaAlta(maestroActualizado.getFechaAlta());
            maestro.setActivo(maestroActualizado.getActivo());
            maestro.setObservaciones(maestroActualizado.getObservaciones());
            maestro.setNombreContactoEmergencia(maestroActualizado.getNombreContactoEmergencia());
            maestro.setTelefonoContactoEmergencia(maestroActualizado.getTelefonoContactoEmergencia());

            if (antecedentes != null && !antecedentes.isEmpty()) {
                maestro.getDocumentos().clear();
                for (MultipartFile archivo : antecedentes) {
                    if (archivo == null || archivo.isEmpty()) {
                        continue;
                    }
                    MaestroDocumento documento = new MaestroDocumento();
                    documento.setMaestro(maestro);
                    documento.setFilename(archivo.getOriginalFilename());
                    documento.setContentType(archivo.getContentType());
                    documento.setSizeBytes(archivo.getSize());
                    documento.setData(archivo.getBytes());
                    maestro.getDocumentos().add(documento);
                }
            }

            Maestro maestroGuardado = maestroRepository.save(maestro);
            return ResponseEntity.ok(maestroGuardado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar el maestro: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<Maestro> maestroOpt = maestroRepository.findById(id);
        if (!maestroOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        maestroRepository.delete(maestroOpt.get());
        return ResponseEntity.ok().build();
    }
}
