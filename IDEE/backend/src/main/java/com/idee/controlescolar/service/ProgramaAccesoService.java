package com.idee.controlescolar.service;

import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aplica restricción por programa para roles acotados (COORDINADOR_ACADEMICO).
 * La seguridad de permisos se valida con @RequierePermiso; este servicio limita el alcance de datos.
 */
@Service
@RequiredArgsConstructor
public class ProgramaAccesoService {

    private final UsuarioRepository usuarioRepository;

    public boolean esCoordinadorAcademico(Usuario u) {
        return u != null && u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO);
    }

    /**
     * Retorna IDs de programas asignados. Para roles no acotados, retorna Set vacío.
     */
    public Set<Long> programaIdsPermitidos(Usuario u) {
        if (!esCoordinadorAcademico(u) || u.getId() == null) {
            return Set.of();
        }
        List<Long> ids = usuarioRepository.findProgramaIdsAsignados(u.getId());
        return ids == null ? Set.of() : new HashSet<>(ids);
    }

    /**
     * True si el usuario puede operar/ver un programaId.
     * Para roles no acotados, siempre true.
     */
    public boolean puedeAccederPrograma(Usuario u, Long programaId) {
        if (!esCoordinadorAcademico(u)) return true;
        if (programaId == null) return false;
        Set<Long> permitidos = programaIdsPermitidos(u);
        return !permitidos.isEmpty() && permitidos.contains(programaId);
    }
}

