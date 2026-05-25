package com.idee.controlescolar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta JSON para fallos de login (401) y bloqueo por tasa (429).
 * Campos opcionales según el caso.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginErrorResponse {
    private int status;
    private String message;
    /** Intentos fallidos acumulados en la ventana actual (incluye este intento). */
    private Integer failedAttempts;
    private Integer maxAttempts;
    /** Cuántos intentos fallidos más se permiten antes del bloqueo (0 = el siguiente quedará bloqueado). */
    private Integer remainingBeforeBlock;
    /** Duración de la ventana de rate limit en segundos. */
    private Integer windowSeconds;
    /** Tras 429: tiempo sugerido antes de reintentar. */
    private Integer retryAfterSeconds;
}
