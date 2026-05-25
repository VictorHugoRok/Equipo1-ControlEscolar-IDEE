package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {
    private String token;
    private String type = "Bearer";
    private Long expiresIn;
    private String refreshToken; // Nuevo refresh token (rotación)
}
