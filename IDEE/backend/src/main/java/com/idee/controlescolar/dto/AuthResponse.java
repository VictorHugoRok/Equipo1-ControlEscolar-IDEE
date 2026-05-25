package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String type = "Bearer";
    private String email;
    private String tipoUsuario;
    private Long usuarioId;
    /** Refresh token para renovar el access token sin volver a login. */
    private String refreshToken;
    /** Tiempo de vida del access token en segundos. */
    private Long expiresIn;
    /** Roles efectivos del usuario (principal + adicionales). */
    private List<String> roles;
    /** Nombre para mostrar (desde Personal, Maestro o Alumno vinculado al usuario). */
    private String nombreCompleto;

    /** Si true, la UI debe forzar cambio de contraseña. */
    private Boolean mustChangePassword;

    public AuthResponse(String token, String email, String tipoUsuario, Long usuarioId) {
        this.token = token;
        this.email = email;
        this.tipoUsuario = tipoUsuario;
        this.usuarioId = usuarioId;
        this.refreshToken = null;
        this.expiresIn = null;
        this.roles = null;
    }

    public AuthResponse(String token, String email, String tipoUsuario, Long usuarioId,
                        String refreshToken, Long expiresIn) {
        this.token = token;
        this.email = email;
        this.tipoUsuario = tipoUsuario;
        this.usuarioId = usuarioId;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.roles = null;
    }

    public AuthResponse(String token, String email, String tipoUsuario, Long usuarioId,
                        String refreshToken, Long expiresIn, List<String> roles) {
        this.token = token;
        this.email = email;
        this.tipoUsuario = tipoUsuario;
        this.usuarioId = usuarioId;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.roles = roles;
    }
}
