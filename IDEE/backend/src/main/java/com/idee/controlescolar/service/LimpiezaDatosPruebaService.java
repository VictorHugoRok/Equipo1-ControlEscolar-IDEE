package com.idee.controlescolar.service;

import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elimina todos los datos de prueba/modalidad para dejar el sistema limpio
 * antes de pasar a uso productivo real.
 * Elimina datos operativos; no borra usuarios administrativos por email (ver lógica futura si aplica).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LimpiezaDatosPruebaService {

    private final CalificacionRepository calificacionRepository;
    private final CertificadoElectronicoRepository certificadoRepository;
    private final TituloElectronicoRepository tituloRepository;
    private final GrupoRepository grupoRepository;
    private final HorarioBloqueRepository horarioBloqueRepository;
    private final AlumnoRepository alumnoRepository;
    private final MaestroRepository maestroRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final UsuarioRepository usuarioRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ResumenLimpieza ejecutarLimpieza() {
        ResumenLimpieza resumen = new ResumenLimpieza();

        // 1. Calificaciones
        long n = calificacionRepository.count();
        calificacionRepository.deleteAll();
        resumen.setCalificacionesEliminadas(n);

        // 2. Certificados electrónicos
        n = certificadoRepository.count();
        certificadoRepository.deleteAll();
        resumen.setCertificadosEliminados(n);

        // 3. Títulos electrónicos
        n = tituloRepository.count();
        tituloRepository.deleteAll();
        resumen.setTitulosEliminados(n);

        // 4. Grupos (referencian asignatura, maestro, alumnos)
        n = grupoRepository.count();
        grupoRepository.deleteAll();
        resumen.setGruposEliminados(n);

        // 5. Horarios
        n = horarioBloqueRepository.count();
        horarioBloqueRepository.deleteAll();
        resumen.setHorariosEliminados(n);

        // 6. Alumnos y sus usuarios
        List<Alumno> alumnos = alumnoRepository.findAll();
        int alumnosElim = 0;
        for (Alumno a : alumnos) {
            Usuario u = a.getUsuario();
            a.setUsuario(null);
            alumnoRepository.save(a);
            alumnoRepository.delete(a);
            if (u != null) {
                usuarioRepository.delete(u);
                alumnosElim++;
            }
        }
        resumen.setAlumnosEliminados(alumnos.size());

        // 7. Desvincular maestros de asignaturas (many-to-many)
        asignaturaRepository.findAll().forEach(asig -> {
            asig.getMaestros().clear();
            asignaturaRepository.save(asig);
        });

        // 8. Maestros y sus usuarios
        List<Maestro> maestros = maestroRepository.findAll();
        for (Maestro m : maestros) {
            Usuario u = m.getUsuario();
            m.setUsuario(null);
            maestroRepository.save(m);
            maestroRepository.delete(m);
            if (u != null) usuarioRepository.delete(u);
        }
        resumen.setMaestrosEliminados(maestros.size());

        // 9. Asignaturas (referencian programa)
        n = asignaturaRepository.count();
        asignaturaRepository.deleteAll();
        resumen.setAsignaturasEliminadas(n);

        // 10. Programas educativos
        n = programaRepository.count();
        programaRepository.deleteAll();
        resumen.setProgramasEliminados(n);

        log.info("Limpieza de datos de prueba completada: {}", resumen);
        return resumen;
    }

    /**
     * Reset TOTAL: borra todos los datos de TODAS las tablas del schema public con TRUNCATE CASCADE.
     * - RESTART IDENTITY: reinicia secuencias/ids.
     * - CASCADE: resuelve llaves foráneas (ej. cohortes -> programas_educativos).
     *
     * Se excluye flyway_schema_history para no “olvidar” migraciones ya aplicadas.
     */
    @Transactional
    public Map<String, Object> resetTotal() {
        List<String> tables = jdbcTemplate.query(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
                (rs, i) -> rs.getString(1)
        );
        List<String> truncables = new ArrayList<>();
        for (String t : (tables != null ? tables : List.<String>of())) {
            if (t == null || t.isBlank()) continue;
            if ("flyway_schema_history".equalsIgnoreCase(t)) continue;
            truncables.add("\"" + t.replace("\"", "\"\"") + "\"");
        }
        if (truncables.isEmpty()) {
            return Map.of("mensaje", "No se encontraron tablas para limpiar.", "tablas", 0);
        }

        String sql = "TRUNCATE TABLE " + String.join(", ", truncables) + " RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        log.warn("RESET TOTAL ejecutado. Tablas truncadas: {}", truncables.size());
        return Map.of(
                "mensaje", "✅ Reset total completado. Se eliminaron todos los datos y se reiniciaron los IDs.",
                "tablas", truncables.size()
        );
    }

    @lombok.Data
    public static class ResumenLimpieza {
        private long calificacionesEliminadas;
        private long certificadosEliminados;
        private long titulosEliminados;
        private long gruposEliminados;
        private long horariosEliminados;
        private long alumnosEliminados;
        private long maestrosEliminados;
        private long asignaturasEliminadas;
        private long programasEliminados;
    }
}
