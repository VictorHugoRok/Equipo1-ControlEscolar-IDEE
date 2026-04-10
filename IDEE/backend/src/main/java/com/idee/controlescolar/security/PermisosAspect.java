package com.idee.controlescolar.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ASPECTO PARA APLICAR VALIDACIÓN DE PERMISOS AUTOMÁTICAMENTE
 * ============================================================
 * 
 * Este aspecto intercepta métodos anotados con @RequierePermiso
 * y valida que el usuario tenga los permisos necesarios ANTES
 * de ejecutar la lógica del método.
 * 
 * Si el usuario no tiene permisos: se lanza excepción 403 FORBIDDEN
 * La operación NO se ejecuta.
 */
@Aspect
@Component
public class PermisosAspect {
    private static final Logger logger = LoggerFactory.getLogger(PermisosAspect.class);

    @Autowired
    private PermisosValidator permisosValidator;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Interceptar métodos anotados con @RequierePermiso
     * Validar permisos ANTES de ejecutar el método
     */
    @Around("@annotation(requierePermiso)")
    public Object validarPermiso(ProceedingJoinPoint joinPoint, RequierePermiso requierePermiso) 
            throws Throwable {
        
        // Obtener el usuario autenticado
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("Intento de acceso sin autenticación a: {}", joinPoint.getSignature());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        // Obtener el nombre de usuario del token JWT
        String username = authentication.getName();
        
        // Obtener el usuario de la base de datos
        Usuario usuario = usuarioRepository.findByEmail(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, 
                "Usuario no encontrado: " + username
            ));

        // Verificar permisos
        String[] permisos = requierePermiso.value();
        boolean tienePermiso;

        if (requierePermiso.requireAll()) {
            // Requiere TODOS los permisos
            tienePermiso = permisosValidator.tieneTodosPermisos(usuario, permisos);
        } else {
            // Requiere AL MENOS UNO
            tienePermiso = permisosValidator.tieneAlgunoPermiso(usuario, permisos);
        }

        if (!tienePermiso) {
            // Registrar intento de acceso no autorizado
            String permisosRequeridos = String.join(", ", permisos);
            logger.warn(
                "ACCESO DENEGADO - Usuario: {}, Rol: {}, Permiso(s) requerido(s): {}, Método: {}",
                usuario.getEmail(),
                usuario.getTipoUsuario(),
                permisosRequeridos,
                joinPoint.getSignature()
            );

            // Lanzar excepción 403 Forbidden
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                requierePermiso.mensaje()
            );
        }

        // Usuario tiene permiso, ejecutar el método
        logger.debug(
            "Permiso OTORGADO - Usuario: {}, Rol: {}, Método: {}",
            usuario.getEmail(),
            usuario.getTipoUsuario(),
            joinPoint.getSignature()
        );

        return joinPoint.proceed();
    }
}
