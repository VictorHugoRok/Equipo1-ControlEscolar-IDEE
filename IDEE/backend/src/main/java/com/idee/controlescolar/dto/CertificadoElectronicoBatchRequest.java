package com.idee.controlescolar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Solicitud para generar 1..50 certificados en un solo disparo.
 * El backend procesa cada alumno de forma aislada y devuelve un resumen (creados / fallidos).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificadoElectronicoBatchRequest {

    /**
     * Config común a todos los certificados del batch.
     */
    @NotNull(message = "La fecha de expedición es requerida")
    private LocalDate fechaExpedicion;

    /**
     * 79 = Total, 80 = Parcial (catálogo SEP)
     */
    @NotBlank(message = "El ID de tipo de certificación es requerido (79=Total, 80=Parcial)")
    private String idTipoCertificacion;

    private String tipoCertificacion;

    private String periodo;
    private String cicloEscolar;

    private String observaciones;

    /**
     * Alumnos a procesar (máximo 50).
     */
    @Valid
    @NotEmpty(message = "Seleccione al menos un alumno")
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull(message = "El ID del alumno es requerido")
        private Long alumnoId;

        @NotNull(message = "El ID del programa es requerido")
        private Long programaId;

        @NotNull(message = "El plantel emisor es requerido")
        private Long plantelId;
    }
}

