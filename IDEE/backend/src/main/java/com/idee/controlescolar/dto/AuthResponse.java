package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String type = "Bearer";
    private String email;
    private String tipoUsuario;
    private Long usuarioId;

    public AuthResponse(String token, String email, String tipoUsuario, Long usuarioId) {
        this.token = token;
        this.email = email;
        this.tipoUsuario = tipoUsuario;
        this.usuarioId = usuarioId;
    }
}
