package com.idee.controlescolar.security;

import java.lang.annotation.*;

/**
 * Anotación para requerir permiso en un método de controlador o servicio
 * 
 * Ejemplo de uso:
 * 
 * @PostMapping("/calificaciones/{id}/confirmar")
 * @RequierePermiso("CONFIRMAR_CALIFICACIONES")
 * public ResponseEntity<Void> confirmarCalificacion(@PathVariable Long id) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequierePermiso {
    /**
     * Permiso(s) requerido(s)
     */
    String[] value();

    /**
     * Si true, se requieren TODOS los permisos
     * Si false, se requiere AL MENOS UNO
     */
    boolean requireAll() default false;

    /**
     * Mensaje de error personalizado
     */
    String mensaje() default "No tienes permisos para realizar esta operación";
}
