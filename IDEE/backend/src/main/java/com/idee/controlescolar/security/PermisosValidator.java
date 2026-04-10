package com.idee.controlescolar.security;

import org.springframework.stereotype.Component;
import com.idee.controlescolar.model.Usuario;
import java.util.*;

/**
 * VALIDADOR DE PERMISOS DEL LADO DEL SERVIDOR
 * ============================================
 * 
 * Este componente valida que los usuarios tengan permisos para realizar
 * operaciones específicas. ESTA VALIDACIÓN ES OBLIGATORIA para seguridad.
 * 
 * No confiar en validación del frontend. El servidor es la fuente única
 * de verdad sobre qué puede hacer cada usuario.
 */
@Component
public class PermisosValidator {

    /**
     * Matriz de permisos por rol
     * Define exactamente qué operaciones puede hacer cada rol
     */
    private static final Map<Usuario.TipoUsuario, Set<String>> PERMISOS_POR_ROL = inicializarPermisos();

    private static Map<Usuario.TipoUsuario, Set<String>> inicializarPermisos() {
        Map<Usuario.TipoUsuario, Set<String>> permisos = new HashMap<>();

        // ADMIN: Solo lectura de calificaciones
        permisos.put(Usuario.TipoUsuario.ADMIN, new HashSet<>(Arrays.asList(
            "VER_PROGRAMAS",
            "VER_DOCENTES",
            "VER_ALUMNOS",
            "VER_CALIFICACIONES",          // ✓ Lectura solamente
            "VER_HORARIOS",
            "ACTUALIZAR_PROGRAMAS",
            "ACTUALIZAR_DOCENTES",
            "ACTUALIZAR_ALUMNOS",
            "ACTUALIZAR_HORARIOS"
            // NOTA: NO incluye EDITAR_CALIFICACIONES, CONFIRMAR_CALIFICACIONES
        )));

        // SECRETARIA_ACADEMICA: Acceso completo
        permisos.put(Usuario.TipoUsuario.SECRETARIA_ACADEMICA, new HashSet<>(Arrays.asList(
            "VER_PROGRAMAS",
            "VER_DOCENTES",
            "VER_ALUMNOS",
            "VER_CALIFICACIONES",
            "EDITAR_CALIFICACIONES",       // ✓ Edición completa
            "CONFIRMAR_CALIFICACIONES",    // ✓ Confirmación
            "VER_HORARIOS",
            "VER_CERTIFICADOS",
            "GENERAR_CERTIFICADOS",
            "VER_CONSTANCIAS",
            "GENERAR_CONSTANCIAS",
            "VER_TITULOS_ELECTRONICOS",
            "GENERAR_TITULOS_ELECTRONICOS",
            "VER_CONFIG_SEP",
            "ACTUALIZAR_CONFIG_SEP",
            "ACTUALIZAR_PROGRAMAS",
            "ACTUALIZAR_DOCENTES",
            "ACTUALIZAR_ALUMNOS",
            "ACTUALIZAR_HORARIOS",
            "FIRMAR_TITULOS"
        )));

        // SECRETARIA_ADMINISTRATIVA: Gestión básica
        permisos.put(Usuario.TipoUsuario.SECRETARIA_ADMINISTRATIVA, new HashSet<>(Arrays.asList(
            "VER_PROGRAMAS",
            "VER_DOCENTES",
            "VER_HORARIOS",
            "VER_CERTIFICADOS",
            "VER_CONSTANCIAS",
            "GENERAR_CERTIFICADOS",
            "GENERAR_CONSTANCIAS"
            // NOTA: NO acceso a calificaciones ni títulos
        )));

        // MAESTRO: Solo ver sus calificaciones y registrar
        permisos.put(Usuario.TipoUsuario.MAESTRO, new HashSet<>(Arrays.asList(
            "VER_CALIFICACIONES_PROPIAS",
            "REGISTRAR_CALIFICACIONES",
            "VER_ALUMNOS_GRUPOS",
            "VER_HORARIO"
        )));

        // ALUMNO: Ver su información
        permisos.put(Usuario.TipoUsuario.ALUMNO, new HashSet<>(Arrays.asList(
            "VER_PERFIL",
            "VER_CALIFICACIONES_PROPIAS",
            "VER_HORARIO",
            "VER_DOCUMENTO_ACADEMICO"
        )));

        return permisos;
    }

    /**
     * Validar si un usuario tiene un permiso específico
     * 
     * @param usuario Usuario a validar
     * @param permiso Permiso solicitado (ej: "EDITAR_CALIFICACIONES")
     * @return true si tiene permiso, false si no
     */
    public boolean tienePermiso(Usuario usuario, String permiso) {
        if (usuario == null || usuario.getTipoUsuario() == null) {
            return false;
        }

        Set<String> permisosRol = PERMISOS_POR_ROL.get(usuario.getTipoUsuario());
        return permisosRol != null && permisosRol.contains(permiso);
    }

    /**
     * Validar si un usuario tiene ALGUNO de los permisos especificados
     * 
     * @param usuario Usuario a validar
     * @param permisos Permisos a verificar
     * @return true si tiene al menos uno
     */
    public boolean tieneAlgunoPermiso(Usuario usuario, String... permisos) {
        return Arrays.stream(permisos).anyMatch(p -> tienePermiso(usuario, p));
    }

    /**
     * Validar si un usuario tiene TODOS los permisos especificados
     * 
     * @param usuario Usuario a validar
     * @param permisos Permisos a verificar
     * @return true si tiene todos
     */
    public boolean tieneTodosPermisos(Usuario usuario, String... permisos) {
        return Arrays.stream(permisos).allMatch(p -> tienePermiso(usuario, p));
    }

    /**
     * Validación específica: ¿Puede este usuario editar calificaciones?
     */
    public boolean puedeEditarCalificaciones(Usuario usuario) {
        return tienePermiso(usuario, "EDITAR_CALIFICACIONES");
    }

    /**
     * Validación específica: ¿Puede este usuario confirmar calificaciones?
     */
    public boolean puedeConfirmarCalificaciones(Usuario usuario) {
        return tienePermiso(usuario, "CONFIRMAR_CALIFICACIONES");
    }

    /**
     * Validación específica: ¿Puede este usuario acceder a títulos?
     */
    public boolean puedeVerTitulos(Usuario usuario) {
        return tienePermiso(usuario, "VER_TITULOS_ELECTRONICOS");
    }

    /**
     * Validación específica: ¿Puede este usuario generar títulos?
     */
    public boolean puedeGenerarTitulos(Usuario usuario) {
        return tienePermiso(usuario, "GENERAR_TITULOS_ELECTRONICOS");
    }

    /**
     * Validación específica: ¿Puede este usuario firmar títulos?
     */
    public boolean puedeFirmarTitulos(Usuario usuario) {
        return tienePermiso(usuario, "FIRMAR_TITULOS");
    }

    /**
     * Validación específica: ¿Puede este usuario ver certificados?
     */
    public boolean puedeVerCertificados(Usuario usuario) {
        return tienePermiso(usuario, "VER_CERTIFICADOS");
    }

    /**
     * Validación específica: ¿Puede este usuario generar certificados?
     */
    public boolean puedeGenerarCertificados(Usuario usuario) {
        return tienePermiso(usuario, "GENERAR_CERTIFICADOS");
    }

    /**
     * Obtener todos los permisos de un usuario
     */
    public Set<String> obtenerPermisos(Usuario usuario) {
        if (usuario == null || usuario.getTipoUsuario() == null) {
            return new HashSet<>();
        }
        Set<String> permisos = PERMISOS_POR_ROL.get(usuario.getTipoUsuario());
        return permisos != null ? new HashSet<>(permisos) : new HashSet<>();
    }

    /**
     * Obtener todos los permisos de un rol
     */
    public Set<String> obtenerPermisosDelRol(Usuario.TipoUsuario rol) {
        Set<String> permisos = PERMISOS_POR_ROL.get(rol);
        return permisos != null ? new HashSet<>(permisos) : new HashSet<>();
    }

    /**
     * Validar si un usuario puede acceder a una sección
     */
    public boolean puedeAccederSeccion(Usuario usuario, String seccion) {
        switch (seccion.toUpperCase()) {
            case "CALIFICACIONES":
                return tienePermiso(usuario, "VER_CALIFICACIONES");
            case "CERTIFICADOS":
                return tienePermiso(usuario, "VER_CERTIFICADOS");
            case "TITULOS":
                return tienePermiso(usuario, "VER_TITULOS_ELECTRONICOS");
            case "CONSTANCIAS":
                return tienePermiso(usuario, "VER_CONSTANCIAS");
            case "CONFIG_SEP":
                return tienePermiso(usuario, "VER_CONFIG_SEP");
            case "PROGRAMAS":
                return tienePermiso(usuario, "VER_PROGRAMAS");
            case "DOCENTES":
                return tienePermiso(usuario, "VER_DOCENTES");
            case "ALUMNOS":
                return tienePermiso(usuario, "VER_ALUMNOS");
            case "HORARIOS":
                return tienePermiso(usuario, "VER_HORARIOS");
            default:
                return false;
        }
    }
}
