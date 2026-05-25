package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta del endpoint /api/auth/me.
 * Solo incluye datos necesarios para la UI; evita serializar Usuario completo
 * (password, relaciones lazy con Alumno/Maestro/Personal) y consultas pesadas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
    private Long id;
    private String email;
    private String tipoUsuario;
    private Boolean activo;
    /** Todos los roles efectivos (principal + adicionales). */
    private List<String> roles;
    /** Nombre para mostrar (desde Personal, Maestro o Alumno vinculado al usuario). */
    private String nombreCompleto;
    /** Si true, la UI debe forzar cambio de contraseña. */
    private Boolean mustChangePassword;
}
